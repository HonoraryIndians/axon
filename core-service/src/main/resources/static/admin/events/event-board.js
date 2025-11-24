(() => {
  const API_BASE = '/api/v1/events';

  const state = {
    events: [],
    selectedEventId: null,
  };

  const elements = {};

  document.addEventListener('DOMContentLoaded', () => {
    cacheElements();
    bindEvents();
    loadEvents();
  });

  /**
   * Caches DOM references used by the events admin UI into the `elements` object.
   *
   * Populates keys: `tableBody`, `createButton`, `eventModal`, `modalTitle`, `eventForm`,
   * `formFields` (with `name`, `description`, `triggerType`, `status`), `payloadInput`,
   * `payloadError`, `modalCloseButtons`, and `eventCountLabel`.
   */
  function cacheElements() {
    elements.tableBody = document.querySelector('[data-event-table-body]');
    elements.createButton = document.querySelector('[data-event-create-button]');
    elements.eventModal = document.getElementById('event-modal');
    elements.modalTitle = document.querySelector('[data-event-modal-title]');
    elements.eventForm = document.getElementById('event-form');
    elements.formFields = {
      name: document.getElementById('event-name'),
      description: document.getElementById('event-description'),
      triggerType: document.getElementById('event-trigger-type'),
      status: document.getElementById('event-status'),
    };
    elements.payloadInput = document.getElementById('event-payload');
    elements.payloadError = document.querySelector('[data-payload-error]');
    elements.modalCloseButtons = document.querySelectorAll('[data-close-modal]');
    elements.eventCountLabel = document.querySelector('[data-event-count]');
  }

  /**
   * Attach UI event handlers for creating events, closing the modal, and submitting the event form.
   *
   * Binds a click listener to the create button to open the creation modal, click listeners to all modal-close controls to close the modal, and a submit listener on the event form to handle form submission.
   */
  function bindEvents() {
    if (elements.createButton) {
      elements.createButton.addEventListener('click', () => {
        openModalForCreate();
      });
    }

    elements.modalCloseButtons.forEach((button) => {
      button.addEventListener('click', closeModal);
    });

    if (elements.eventForm) {
      elements.eventForm.addEventListener('submit', handleSubmit);
    }
  }

  /**
   * Fetches the event list from the API, updates the local state, and refreshes the table and count.
   *
   * On success, replaces state.events with the fetched list, re-renders the event table, and updates the event count.
   * On failure, logs the error, renders an error row in the table, and sets the event count to 0.
   */
  async function loadEvents() {
    try {
      toggleTableLoading(true);
      const response = await fetch(API_BASE);
      if (!response.ok) {
        throw new Error('이벤트 목록을 불러오지 못했습니다.');
      }
      state.events = await response.json();
      renderEventTable();
      updateEventCount(state.events.length);
    } catch (error) {
      console.error(error);
      renderErrorRow('목록을 가져오는 중 오류가 발생했습니다.');
      updateEventCount(0);
    } finally {
      toggleTableLoading(false);
    }
  }

  /**
   * Render the events list into the table body or display a friendly empty-state row when there are no events.
   *
   * Populates the cached table body element from `state.events`, formats status, payload, and dates for display,
   * and attaches edit and delete click handlers for each rendered row.
   */
  function renderEventTable() {
    elements.tableBody.innerHTML = '';

    if (!state.events.length) {
      elements.tableBody.innerHTML = `
        <tr>
          <td colspan="8" class="px-4 py-6 text-center text-sm text-neutral-500">
            아직 등록된 이벤트가 없습니다. 오른쪽 상단의 "새 이벤트" 버튼으로 추가해 보세요.
          </td>
        </tr>
      `;
      return;
    }

    state.events.forEach((event) => {
      const row = document.createElement('tr');
      row.className = 'hover:bg-neutral-50';
      row.innerHTML = `
        <td class="px-4 py-4">
          <input type="checkbox" class="w-4 h-4 rounded border-neutral-300" data-event-checkbox value="${event.id}">
        </td>
        <td class="px-4 py-4">
          <button class="text-neutral-900 hover:text-primary-600" data-event-edit="${event.id}">${event.name}</button>
        </td>
        <td class="px-4 py-4">
          <span class="px-2 py-1 text-xs font-medium rounded-full ${badgeClass(event.status)}">${translateStatus(event.status)}</span>
        </td>
        <td class="px-4 py-4 text-sm text-neutral-700">${event.triggerType ?? '-'}</td>
        <td class="px-4 py-4 text-sm text-neutral-700">
          <code class="inline-block px-2 py-1 text-xs bg-neutral-100 rounded">${formatPayload(event.triggerPayload)}</code>
        </td>
        <td class="px-4 py-4 text-sm text-neutral-600">${event.description ?? '-'}</td>
        <td class="px-4 py-4 text-sm text-neutral-600">${formatDate(event.updatedAt)}</td>
        <td class="px-4 py-4 text-right">
          <div class="flex items-center justify-end space-x-2">
            <button class="text-sm text-neutral-500 hover:text-primary-600" data-event-edit="${event.id}"><i class="fa-regular fa-pen-to-square"></i></button>
            <button class="text-sm text-neutral-500 hover:text-red-500" data-event-delete="${event.id}"><i class="fa-regular fa-trash-can"></i></button>
          </div>
        </td>
      `;

      row.querySelectorAll('[data-event-edit]').forEach((btn) => {
        btn.addEventListener('click', () => openModalForEdit(event.id));
      });
      row.querySelector('[data-event-delete]').addEventListener('click', () => handleDelete(event.id));

      elements.tableBody.appendChild(row);
    });
  }

  /**
   * Render a single full-width error row inside the events table body.
   * @param {string} message - Text to display; inserted into a table row that spans all 8 columns and is styled as an inline red message.
   */
  function renderErrorRow(message) {
    elements.tableBody.innerHTML = `
      <tr>
        <td colspan="8" class="px-4 py-6 text-center text-sm text-red-600">${message}</td>
      </tr>
    `;
  }

  /**
   * Display a loading placeholder in the events table when loading.
   * When `isLoading` is true, replaces the table body with a loading row.
   * @param {boolean} isLoading - If `true`, show the loading placeholder; if `false`, do nothing.
   */
  function toggleTableLoading(isLoading) {
    if (isLoading) {
      renderErrorRow('불러오는 중...');
    }
  }

  /**
   * Prepare the event modal for creating a new event and open it.
   *
   * Clears the current selection, resets and clears form fields, sets the status to 'ACTIVE',
   * clears any payload and payload error, updates the modal title to '이벤트 생성', and opens the modal.
   */
  function openModalForCreate() {
    state.selectedEventId = null;
    elements.modalTitle.textContent = '이벤트 생성';
    elements.eventForm.reset();
    elements.formFields.name.value = '';
    elements.formFields.description.value = '';
    elements.formFields.triggerType.value = '';
    elements.formFields.status.value = 'ACTIVE';
    elements.payloadInput.value = '';
    elements.payloadError.textContent = '';
    openModal();
  }

  /**
   * Populate the event modal with the selected event's data and open the modal for editing.
   *
   * If the event ID does not match any loaded event, logs a warning and leaves the modal closed.
   * @param {string|number} eventId - The identifier of the event to load into the modal.
   */
  function openModalForEdit(eventId) {
    const event = state.events.find((item) => item.id === eventId);
    if (!event) {
      console.warn('Event not found', eventId);
      return;
    }

    state.selectedEventId = eventId;
    elements.modalTitle.textContent = '이벤트 수정';
    elements.formFields.name.value = event.name ?? '';
    elements.formFields.description.value = event.description ?? '';
    elements.formFields.triggerType.value = event.triggerType ?? '';
    elements.formFields.status.value = event.status ?? 'ACTIVE';
    elements.payloadInput.value = JSON.stringify(event.triggerPayload ?? {}, null, 2);
    elements.payloadError.textContent = '';
    openModal();
  }

  /**
   * Handle the event form submission for creating or updating an event.
   *
   * Validates the JSON payload and required trigger type, sends a POST request to create
   * or a PUT request to update the event on the server, closes the modal and refreshes
   * the event list on success, and displays an error message in the payload error element on failure.
   *
   * @param {Event} event - The submit event from the event form.
   */
  async function handleSubmit(event) {
    event.preventDefault();
    elements.payloadError.textContent = '';

    const payload = parsePayload(elements.payloadInput.value);
    if (payload === null) {
      elements.payloadError.textContent = '올바른 JSON 형식이 아닙니다.';
      return;
    }

    const requestBody = {
      name: elements.formFields.name.value,
      description: elements.formFields.description.value,
      triggerType: elements.formFields.triggerType.value,
      triggerPayload: payload,
      status: elements.formFields.status.value,
    };

    if (!requestBody.triggerType) {
      elements.payloadError.textContent = '트리거 타입을 선택해 주세요.';
      return;
    }

    try {
      const response = await fetch(state.selectedEventId ? `${API_BASE}/${state.selectedEventId}` : API_BASE, {
        method: state.selectedEventId ? 'PUT' : 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestBody),
      });

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(errText || '요청 처리 중 오류가 발생했습니다.');
      }

      closeModal();
      await loadEvents();
    } catch (error) {
      console.error(error);
      elements.payloadError.textContent = error.message;
    }
  }

  /**
   * Delete an event on the server after user confirmation and refresh the event list.
   *
   * Prompts the user to confirm deletion; if confirmed, sends a DELETE request for the given event
   * and reloads the event list on success. On failure, logs the error to the console and displays
   * an alert with the error message.
   *
   * @param {string|number} eventId - The identifier of the event to delete.
   */
  async function handleDelete(eventId) {
    if (!confirm('이 이벤트를 삭제하시겠습니까?')) {
      return;
    }

    try {
      const response = await fetch(`${API_BASE}/${eventId}`, { method: 'DELETE' });
      if (!response.ok) {
        throw new Error('삭제에 실패했습니다.');
      }
      await loadEvents();
    } catch (error) {
      console.error(error);
      alert(error.message);
    }
  }

  /**
   * Parse a JSON payload string from the payload input and return its object representation.
   * @param {string} rawValue - Raw JSON text from the payload input.
   * @returns {object|null} The parsed object; an empty object ({}) if `rawValue` is empty or whitespace, or `null` if `rawValue` contains invalid JSON.
   */
  function parsePayload(rawValue) {
    if (!rawValue || !rawValue.trim()) {
      return {};
    }
    try {
      return JSON.parse(rawValue);
    } catch (error) {
      console.warn('Invalid JSON payload', error);
      return null;
    }
  }

  /**
   * Opens the event modal dialog.
   *
   * If the modal element is not present in the DOM cache, the function does nothing.
   */
  function openModal() {
    elements.eventModal?.classList.remove('hidden');
  }

  /**
   * Hides the event modal by adding the 'hidden' CSS class if the modal element exists.
   */
  function closeModal() {
    elements.eventModal?.classList.add('hidden');
  }

  /**
   * Provide the CSS class string used to style a status badge.
   *
   * @param {string} status - Event status; when equal to `'ACTIVE'` the returned classes style a green badge, otherwise a neutral badge.
   * @returns {string} The space-separated CSS classes for the badge styling.
   */
  function badgeClass(status) {
    return status === 'ACTIVE'
      ? 'bg-green-50 text-green-600 border border-green-200'
      : 'bg-neutral-100 text-neutral-600 border border-neutral-200';
  }

  /**
   * Convert an event status code into a human-readable Korean label.
   * @param {string|undefined|null} status - The status code to translate (e.g., 'ACTIVE', 'INACTIVE').
   * @returns {string} The Korean label for known status codes ('활성' or '비활성'), the original `status` if present, or '-' when no status is provided.
   */
  function translateStatus(status) {
    switch (status) {
      case 'ACTIVE':
        return '활성';
      case 'INACTIVE':
        return '비활성';
      default:
        return status ?? '-';
    }
  }

  /**
   * Produce a compact JSON string representation of an event trigger payload or "{}" when the payload is empty or not serializable.
   * @param {Object|null|undefined} payload - The payload to serialize; empty objects, `null`, or `undefined` are treated as empty.
   * @returns {string} A JSON string of the payload, or `"{}"` if the payload is empty or serialization fails.
   */
  function formatPayload(payload) {
    if (!payload || Object.keys(payload).length === 0) {
      return '{}';
    }
    try {
      return JSON.stringify(payload);
    } catch (error) {
      return '{}';
    }
  }

  /**
   * Format an ISO 8601 date/time string for display using the Korean locale.
   * @param {string} isoString - The ISO 8601 date/time string to format.
   * @returns {string} A localized date/time string in the 'ko-KR' format (year, month, day, hour, minute), or '-' if the input is missing or cannot be parsed.
   */
  function formatDate(isoString) {
    if (!isoString) {
      return '-';
    }
    try {
      const date = new Date(isoString);
      return new Intl.DateTimeFormat('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
    } catch (error) {
      return '-';
    }
  }

  /**
   * Update the displayed event count in the UI.
   * @param {number} count - The number of events to show; if `count` is not a finite number, the label is set to `0`.
   */
  function updateEventCount(count) {
    if (elements.eventCountLabel) {
      elements.eventCountLabel.textContent = Number.isFinite(count) ? count : 0;
    }
  }
})();