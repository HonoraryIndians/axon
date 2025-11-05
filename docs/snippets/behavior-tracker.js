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

  function BehaviorTracker() {
    this.config = { ...DEFAULTS };
    this.state = {
      events: [],
      lastSentAt: new Map(),
      initialized: false,
      refreshing: false
    };
  }

  BehaviorTracker.prototype.init = async function init(userConfig = {}) {
    if (this.state.initialized) {
      this.log('Tracker already initialized, skipping init.');
      return this.state.initialized;
    }

    this.config = { ...DEFAULTS, ...userConfig };
    this.state.initialized = this.fetchAndBind();

    if (this.config.autoRefreshMs > 0) {
      setInterval(() => this.refreshEvents(), this.config.autoRefreshMs);
    }

    this.log('Behavior tracker initialized with config:', this.config);
    return this.state.initialized;
  };

  BehaviorTracker.prototype.fetchAndBind = async function fetchAndBind() {
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

  BehaviorTracker.prototype.fetchActiveEvents = async function fetchActiveEvents() {
    const url = this.resolveUrl(this.config.eventsEndpoint);
    const headers = await this.buildAuthHeaders();

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

  BehaviorTracker.prototype.registerHandlers = function registerHandlers() {
    this.registerPageViewHandlers();
    this.registerClickHandlers();
  };

  BehaviorTracker.prototype.registerPageViewHandlers = function registerPageViewHandlers() {
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

  BehaviorTracker.prototype.handlePageView = function handlePageView() {
    const url = window.location.pathname + window.location.search;
    const matching = this.state.events.filter(
      (event) =>
        event.triggerType === TriggerType.PAGE_VIEW &&
        this.matchesUrl(event.triggerPayload, url)
    );

    matching.forEach((event) => {
      this.sendEvent(event, {
        pageUrl: window.location.href,
        referrer: document.referrer || null
      });
    });
  };

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

  BehaviorTracker.prototype.registerClickHandlers = function registerClickHandlers() {
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

  BehaviorTracker.prototype.sendEvent = async function sendEvent(eventDefinition, properties = {}) {
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

    const payload = {
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
    headers['Content-Type'] = 'application/json';

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
        credentials: this.config.withCredentials ? 'include' : 'same-origin',
        keepalive: true
      });

      if (!response.ok) {
        throw new Error(`Failed to send event: ${response.status}`);
      }

      this.log('Sent behavior event', payload);
    } catch (error) {
      console.error('[AxonTracker] Failed to send behavior event', error);
    }
  };

  BehaviorTracker.prototype.buildAuthHeaders = async function buildAuthHeaders() {
    const headers = {};
    const token = await resolveValue(this.config.tokenProvider);
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
    return headers;
  };

  BehaviorTracker.prototype.resolveUrl = function resolveUrl(path) {
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
      console.debug('[AxonTracker]', ...args);
    }
  };

  function getSanitizedText(element) {
    const text = element.innerText || element.textContent || '';
    return text.trim().slice(0, 200);
  }

  async function resolveValue(candidate) {
    if (typeof candidate === 'function') {
      return candidate();
    }
    return candidate ?? null;
  }

  global.AxonBehaviorTracker = new BehaviorTracker();
})(window);
