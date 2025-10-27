document.addEventListener('DOMContentLoaded', function () {
    const eventTableBody = document.querySelector('.campaign-table tbody');
    const totalEventsSpan = document.querySelector('.table-header .text-neutral-600');
    const token = common.getCookie("accessToken");

    // 총 이벤트 개수를 가져와 표시
    fetch('/api/v1/campaign/events/count')
        .then(response => response.json())
        .then(count => {
            totalEventsSpan.textContent = `총 ${count}개 이벤트`;
        })
        .catch(error => {
            console.error('총 이벤트 개수 가져오기 오류:', error);
            totalEventsSpan.textContent = '총 이벤트를 불러오지 못했습니다.';
        });

    // 이벤트 목록을 가져와 테이블에 채우기
    fetch('/api/v1/campaign/events')
        .then(response => response.json())
        .then(data => {
            eventTableBody.innerHTML = ''; // 기존 테이블 내용 지우기
            data.forEach(event => {
                const row = document.createElement('tr');
                const eventTypeMap = {
                    'FIRST_COME_FIRST_SERVE': '선착순',
                    'COUPON': '쿠폰',
                    'GIVEAWAY': '경품'
                };
                const eventTypeText = eventTypeMap[event.eventType];
                row.innerHTML = `
                    <td><label><input type="checkbox" class="check"></label></td>
                    <td><span class="link" data-event-id="${event.id}">${event.name}</span></td>
                    <td><span class="badge"><span class="dot"></span> ${event.status}</span></td>
                    <td>${eventTypeText}</td>
                    <td>${new Date(event.start_date).toLocaleDateString()} ~ ${new Date(event.end_date).toLocaleDateString()}</td>
                    <td>
                        <div class="progress">
                            <div class="bar" style="width:${(event.participantCount / event.limitCount) * 100}%"></div>
                        </div>
                        <span class="progress-text">${event.participantCount}/${event.limitCount}</span>
                    </td>
                    <td>${new Date(event.created_at).toLocaleDateString()}</td>
                    <td>
                        <div class="action-menu-container">
                            <button class="action-menu-trigger" data-event-id="${event.id}" data-event-status="${event.status}">
                                <i class="fa-solid fa-ellipsis-vertical"></i>
                            </button>
                        </div>
                    </td>
                `;
                eventTableBody.appendChild(row);
            });
        })
        .catch(error => {
            console.error('이벤트 가져오기 오류:', error);
            eventTableBody.innerHTML = '<tr><td colspan="8">목록 불러오기를 실패했습니다. 다시 시도해주세요.</td></tr>';
        });

    let activeDropdown = null; // 현재 열려있는 드롭다운을 추적하기 위한 변수

    // 액션 메뉴 트리거 및 항목에 대한 이벤트 위임 (document.body에 리스너 부착)
    document.body.addEventListener('click', function(event) {
        const trigger = event.target.closest('.action-menu-trigger');
        const actionItem = event.target.closest('.action-menu-item');

        if (trigger) {
            event.stopPropagation();
            if (activeDropdown) activeDropdown.remove();

            const eventId = trigger.dataset.eventId;
            const currentStatus = trigger.dataset.eventStatus;
            const rect = trigger.getBoundingClientRect();

            const dropdown = document.createElement('div');
            dropdown.classList.add('action-menu-dropdown');
            let dropdownContent = `
                <a href="#" class="action-menu-item edit-event" data-event-id="${eventId}">수정</a>
                <a href="#" style="color: tomato" class="action-menu-item delete-event" data-event-id="${eventId}">삭제</a>
            `;

            if (currentStatus === 'DRAFT') {
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-event-id="${eventId}" data-new-status="ACTIVE"><b>ACTIVE</b>로 상태 변경</a>`;
            } else if (currentStatus === 'ACTIVE') {
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-event-id="${eventId}" data-new-status="PAUSED"><b>PAUSED</b>로 상태 변경</a>`;
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-event-id="${eventId}" data-new-status="ENDED"><b>ENDED</b>로 상태 변경</a>`;
            } else if (currentStatus === 'PAUSED') {
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-event-id="${eventId}" data-new-status="ACTIVE"><b>ACTIVE</b>로 상태 변경</a>`;
            }

            dropdown.innerHTML = dropdownContent;
            dropdown.style.position = 'absolute';
            dropdown.style.top = `${rect.bottom + window.scrollY}px`;
            dropdown.style.left = `${rect.left + window.scrollX}px`;
            dropdown.style.zIndex = '1000';

            document.body.appendChild(dropdown);
            activeDropdown = dropdown;

        } else if (actionItem) {
            const eventId = actionItem.dataset.eventId;
            const eventNameElement = document.querySelector(`tr .link[data-event-id="${eventId}"]`);
            const eventName = eventNameElement ? eventNameElement.textContent : `이벤트 ${eventId}`;

            if (actionItem.classList.contains('edit-event')) {
                modalHandler.showEditModal(eventId, eventName);
            } else if (actionItem.classList.contains('delete-event')) {
                modalHandler.showDeleteModal(eventId, eventName);
            } else if (actionItem.classList.contains('change-status')) {
                const newStatus = actionItem.dataset.newStatus;
                const currentStatus = document.querySelector(`.action-menu-trigger[data-event-id="${eventId}"]`).dataset.eventStatus;
                modalHandler.showStatusModal(eventId, eventName, currentStatus, newStatus);
            }

            if (activeDropdown) activeDropdown.remove();
            activeDropdown = null;

        } else {
            if (activeDropdown) {
                activeDropdown.remove();
                activeDropdown = null;
            }
        }
    });

    // "새 캠페인 생성" 버튼에 이벤트 리스너 추가
    const createCampaignBtn = document.getElementById('create-campaign-btn');
    if (createCampaignBtn) {
        createCampaignBtn.addEventListener('click', (event) => {
            event.preventDefault(); // a 태그의 기본 동작 방지
            modalHandler.showCreateCampaignModal();
        });
    }
});
