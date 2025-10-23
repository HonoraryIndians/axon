
document.addEventListener('DOMContentLoaded', function () {
    const eventTableBody = document.querySelector('.campaign-table tbody');
    const totalEventsSpan = document.querySelector('.table-header .text-neutral-600');

    // Fetch total event count
    fetch('/api/v1/campaign/events/count')
        .then(response => response.json())
        .then(count => {
            totalEventsSpan.textContent = `총 ${count}개 이벤트`;
        })
        .catch(error => {
            console.error('Error fetching total event count:', error);
            totalEventsSpan.textContent = '총 이벤트를 불러오지 못했습니다.';
        });

    fetch('/api/v1/campaign/events')
        .then(response => response.json())
        .then(data => {
            eventTableBody.innerHTML = ''; // Clear the existing table body
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
                    <td><span class="link">${event.name}</span></td>
                    <td><span class="badge"><span class="dot"></span> ${event.status}</span></td>
                    <td>${eventTypeText}</td>
                    <td>${new Date(event.start_date).toLocaleDateString()} ~ ${new Date(event.end_date).toLocaleDateString()}</td>
                    <td>
                        <div class="progress">
                            <div class="bar" style="width:0%"></div>
                        </div>
                        <span class="progress-text">0/${event.limitCount}</span>
                    </td>
                    <td>${new Date(event.created_at).toLocaleDateString()}</td>
                    <td>
                        <div class="action-menu-container">
                            <button class="action-menu-trigger">
                                <i class="fa-solid fa-ellipsis-vertical"></i>
                            </button>
                            <div class="action-menu-dropdown hidden">
                                <a href="#" class="action-menu-item edit-event" data-event-id="${event.id}">수정</a>
                                <a href="#" class="action-menu-item delete-event" data-event-id="${event.id}">삭제</a>
                            </div>
                        </div>
                    </td>
                `;
                eventTableBody.appendChild(row);
            });
        })
        .catch(error => {
            console.error('Error fetching events:', error);
            eventTableBody.innerHTML = '<tr><td colspan="8">목록 불러오기를 실패했습니다. 다시 시도해주세요.</td></tr>';
        });

    // Event delegation for action menu triggers
    eventTableBody.addEventListener('click', function(event) {
        const trigger = event.target.closest('.action-menu-trigger');
        if (trigger) {
            const container = trigger.closest('.action-menu-container');
            const dropdown = container.querySelector('.action-menu-dropdown');
            dropdown.classList.toggle('hidden');
        } else {
            // Close all dropdowns if click is outside
            document.querySelectorAll('.action-menu-dropdown').forEach(dropdown => {
                if (!dropdown.classList.contains('hidden')) {
                    dropdown.classList.add('hidden');
                }
            });
        }

        const editButton = event.target.closest('.edit-event');
        if (editButton) {
            const eventId = editButton.dataset.eventId;
            alert('수정 이벤트 ID: ' + eventId);
            // TODO: Implement actual edit functionality, e.g., redirect to edit page
        }

        const deleteButton = event.target.closest('.delete-event');
        if (deleteButton) {
            const eventId = deleteButton.dataset.eventId;
            if (confirm('정말로 이 이벤트를 삭제하시겠습니까? (ID: ' + eventId + ')')) {
                // TODO: Implement actual delete functionality, e.g., send DELETE request
                alert('삭제 이벤트 ID: ' + eventId);
            }
        }
    });
});

