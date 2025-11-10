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
            <button class="text-sm text-neutral-500 hover:text-primary-600" data-event-edit="${event.id}">수정</button>
            <button class="text-sm text-neutral-500 hover:text-red-500" data-event-delete="${event.id}">삭제</button>
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

  function renderErrorRow(message) {
    elements.tableBody.innerHTML = `
      <tr>
        <td colspan="8" class="px-4 py-6 text-center text-sm text-red-600">${message}</td>
      </tr>
    `;
  }

  function toggleTableLoading(isLoading) {
    if (isLoading) {
      renderErrorRow('불러오는 중...');
    }
  }

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

  function openModal() {
    elements.eventModal?.classList.remove('hidden');
  }

  function closeModal() {
    elements.eventModal?.classList.add('hidden');
  }

  function badgeClass(status) {
    return status === 'ACTIVE'
      ? 'bg-green-50 text-green-600 border border-green-200'
      : 'bg-neutral-100 text-neutral-600 border border-neutral-200';
  }

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

  function updateEventCount(count) {
    if (elements.eventCountLabel) {
      elements.eventCountLabel.textContent = Number.isFinite(count) ? count : 0;
    }
  }
})();
