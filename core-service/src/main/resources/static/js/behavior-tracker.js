(function (global) {
  'use strict';

  const DEFAULTS = {
    apiBaseUrl: '',
    eventsEndpoint: '/api/v1/events/active',
    collectEndpoint: '/api/v1/behavior/events',
    autoRefreshMs: 5 * 60 * 1000,
    debug: false,
    withCredentials: true,
    cooldownMs: 1500
  };

  const TriggerType = {
    PAGE_VIEW: 'PAGE_VIEW',
    CLICK: 'CLICK'
  };

  /**
   * 메인 트래커 클래스
   * - 이벤트 정의를 주기적으로 조회하고
   * - 지정된 트리거에 맞춰 클라이언트 행동을 수집
   * - entry-service에 전달한다.
   */
  function BehaviorTracker() {
    console.log('[AxonBehaviorTracker] constructor');
    this.config = { ...DEFAULTS };
    this.state = {
      events: [],
      lastSentAt: new Map(),
      initialized: false,
      refreshing: false
    };
  }

  /**
   * 사용자 정의 설정을 적용하고 트래커를 초기화한다.
   */
  BehaviorTracker.prototype.init = async function init(userConfig = {}) {
    console.log('[AxonBehaviorTracker] init', userConfig);
    if (this.state.initialized) {
      this.log('Tracker already initialized, skipping init.');
      return this.state.initialized;
    }

    this.config = { ...DEFAULTS, ...userConfig };
    console.log('[AxonBehaviorTracker] config', this.config);
    this.state.initialized = await this.fetchAndBind();

    if (this.config.autoRefreshMs > 0) {
      setInterval(() => this.refreshEvents(), this.config.autoRefreshMs);
    }

    this.log('Behavior tracker initialized with config:', this.config);
    return this.state.initialized;
  };

  /**
   * 이벤트 정의를 조회하고 핸들러를 등록한다.
   */
  BehaviorTracker.prototype.fetchAndBind = async function fetchAndBind() {
    console.log('[AxonBehaviorTracker] fetchAndBind start');
    try {
      const events = await this.fetchActiveEvents();
      this.state.events = events || [];
      this.log('Fetched active events:', events);
      this.registerHandlers();
      return true;
    } catch (error) {
      console.error('[AxonTracker] Failed to initialize tracker', error);
      return false;
    }
  };

  BehaviorTracker.prototype.refreshEvents = async function refreshEvents() {
    console.log('[AxonBehaviorTracker] refreshEvents');
    if (this.state.refreshing) {
      return;
    }
    this.state.refreshing = true;
    try {
      const events = await this.fetchActiveEvents();
      this.state.events = events || [];
      this.log('Refreshed active events:', events);
    } catch (error) {
      console.error('[AxonTracker] Failed to refresh events', error);
    } finally {
      this.state.refreshing = false;
    }
  };

  /**
   * 활성 이벤트 목록을 코어 서비스에서 가져온다.
   */
  BehaviorTracker.prototype.fetchActiveEvents = async function fetchActiveEvents() {
    const url = this.resolveUrl(this.config.eventsEndpoint);
    const headers = await this.buildAuthHeaders();
    console.log('[AxonBehaviorTracker] headers', headers);

    console.log('[AxonBehaviorTracker] fetch active events', url);
    const response = await fetch(url, {
      method: 'GET',
      headers,
      credentials: this.config.withCredentials ? 'include' : 'same-origin'
    });

    if (!response.ok) {
      throw new Error(`Failed to fetch active events: ${response.status}`);
    }

    return response.json();
  };

  /**
   * 트리거 타입에 따라 감지 핸들러를 등록한다.
   */
  BehaviorTracker.prototype.registerHandlers = function registerHandlers() {
    this.registerPageViewHandlers();
        this.registerClickHandlers();
    this.registerClickHandlers();
  };

  /**
   * History API를 감시해 PAGE_VIEW 이벤트를 감지한다.
   */
  BehaviorTracker.prototype.registerPageViewHandlers = function registerPageViewHandlers() {
    console.log('[AxonBehaviorTracker] registerPageViewHandlers');
    const check = () => this.handlePageView();

    window.addEventListener('load', check, { once: true });
    window.addEventListener('popstate', () => setTimeout(check, 0));

    ['pushState', 'replaceState'].forEach((method) => {
      const original = window.history[method];
      if (typeof original === 'function') {
        window.history[method] = (...args) => {
          const result = original.apply(window.history, args);
          setTimeout(check, 0);
          return result;
        };
      }
    });

    check();
  };

  /**
   * 현재 URL이 등록된 패턴과 일치하는지 확인하고, 해당 이벤트를 전송한다.
   */
  BehaviorTracker.prototype.handlePageView = function handlePageView() {
    console.log('[AxonBehaviorTracker] handlePageView');
    const fullPath = window.location.pathname + window.location.search;
    const pathOnly = window.location.pathname;
    const matching = this.state.events.filter(
      (event) =>
        event.triggerType === TriggerType.PAGE_VIEW &&
        (this.matchesUrl(event.triggerPayload, fullPath) || this.matchesUrl(event.triggerPayload, pathOnly))
    );

    matching.forEach((event) => {
      this.sendEvent(event, {
        pageUrl: window.location.href,
        referrer: document.referrer || null
      });
    });
  };

  /**
   * URL 패턴(와일드카드 지원)을 기반으로 현재 경로가 조건에 맞는지 검사한다.
   */
  BehaviorTracker.prototype.matchesUrl = function matchesUrl(payload, currentPath) {
    if (!payload || !payload.urlPattern) {
      return true;
    }
    const pattern = payload.urlPattern
      .replace(/[.+?^${}()|[\]\\]/g, '\\$&')
      .replace(/\*/g, '.*');
    const regexp = new RegExp(`^${pattern}$`);
    return regexp.test(currentPath);
  };

  /**
   * 캡처 가능한 클릭 이벤트를 등록하고 전송한다.
   */
  BehaviorTracker.prototype.registerClickHandlers = function registerClickHandlers() {
    console.log('[AxonBehaviorTracker] registerClickHandlers');
    const clickEvents = this.state.events.filter(
      (event) => event.triggerType === TriggerType.CLICK
    );

    if (clickEvents.length === 0) {
      return;
    }

    document.addEventListener('click', (event) => {
      const { target } = event;
      if (!target) {
        return;
      }

      clickEvents.forEach((definition) => {
        const selector = definition.triggerPayload?.selector;
        if (!selector) {
          return;
        }

        const matchedElement = target.closest(selector);
        if (matchedElement) {
          this.sendEvent(definition, {
            pageUrl: window.location.href,
            referrer: document.referrer || null,
            selector,
            elementText: getSanitizedText(matchedElement),
            elementTag: matchedElement.tagName,
            elementId: matchedElement.id || null
          });
        }
      });
    });
  };

  /**
   * 이벤트 전송 로직
   * - 동일 이벤트의 과도한 전송을 막기 위해 cooldown을 적용한다.
   */
  BehaviorTracker.prototype.sendEvent = async function sendEvent(eventDefinition, properties = {}) {
    console.log('[AxonBehaviorTracker] sendEvent', eventDefinition);
    if (!eventDefinition || !eventDefinition.triggerType) {
      this.log('Skip sending event: invalid definition', eventDefinition);
      return;
    }

    const now = Date.now();
    const lastSent = this.state.lastSentAt.get(eventDefinition.id);
    if (lastSent && now - lastSent < this.config.cooldownMs) {
      this.log('Skip sending event due to cooldown', eventDefinition);
      return;
    }

    this.state.lastSentAt.set(eventDefinition.id, now);

    const requestPayload = {
      eventId: eventDefinition.id,
      eventName: eventDefinition.name,
      triggerType: eventDefinition.triggerType,
      occurredAt: new Date().toISOString(),
      pageUrl: properties.pageUrl || window.location.href,
      referrer: properties.referrer ?? document.referrer ?? null,
      userId: await resolveValue(this.config.userIdProvider),
      sessionId: await resolveValue(this.config.sessionIdProvider),
      properties
    };

    const url = this.resolveUrl(this.config.collectEndpoint);
    const headers = await this.buildAuthHeaders();
    console.log('[AxonBehaviorTracker] headers', headers);
    headers['Content-Type'] = 'application/json';

    try {
      console.log('[AxonBehaviorTracker] sending event', url, requestPayload);
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(requestPayload),
        credentials: this.config.withCredentials ? 'include' : 'same-origin',
        keepalive: true
      });

      if (!response.ok) {
        throw new Error(`Failed to send event: ${response.status}`);
      }

      this.log('Sent behavior event', requestPayload);
    } catch (error) {
      console.error('[AxonTracker] Failed to send behavior event', error);
    }
  };

  /**
   * Authorization 헤더를 구성한다.
   */
  BehaviorTracker.prototype.buildAuthHeaders = async function buildAuthHeaders() {
    console.log('[AxonBehaviorTracker] buildAuthHeaders');
    const headers = {};
    const token = await resolveValue(this.config.tokenProvider);
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    return headers;
  };

  /**
   * 상대 경로를 apiBaseUrl 기준으로 절대 URL로 변환한다.
   */
  BehaviorTracker.prototype.resolveUrl = function resolveUrl(path) {
    console.log('[AxonBehaviorTracker] resolveUrl', path);
    if (!path) {
      return '';
    }
    if (path.startsWith('http://') || path.startsWith('https://')) {
      return path;
    }
    const base = this.config.apiBaseUrl || '';
    if (!base) {
      return path;
    }
    return `${base.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
  };

  BehaviorTracker.prototype.log = function log(...args) {
    if (this.config.debug) {
      console.log('[AxonTracker]', ...args);
    }
  };

  /**
   * 요소의 텍스트를 최대 200자까지 정제한다.
   */
  function getSanitizedText(element) {
    const text = element.innerText || element.textContent || '';
    return text.trim().slice(0, 200);
  }

  /**
   * 값이 함수라면 실행하고, 아니면 그대로 반환한다.
   */
  async function resolveValue(candidate) {
    console.log('[AxonBehaviorTracker] resolveValue', candidate);
    if (typeof candidate === 'function') {
      return candidate();
    }
    return candidate ?? null;
  }

  global.AxonBehaviorTracker = new BehaviorTracker();
})(window);
