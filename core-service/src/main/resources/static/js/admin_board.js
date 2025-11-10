document.addEventListener('DOMContentLoaded', () => {
    const activityTableBody = document.querySelector('.campaign-table tbody');
    const totalActivitiesSpan = document.querySelector('.table-header .text-neutral-600');
    const token = common.getCookie('accessToken');

    const ACTIVITY_PROGRESS_DEFAULT_LIMIT = 1;

    const formatDate = (value) => value ? new Date(value).toLocaleDateString() : '-';
    const formatProgress = (count, limit) => `${count}/${limit ?? '∞'}`;
    const resolveProgressWidth = (count, limit) => {
        if (!limit || limit <= 0) return 0;
        return Math.min(100, Math.round((count / limit) * 100));
    };

    fetch('/api/v1/campaign/activities/count')
        .then((response) => response.json())
        .then((count) => {
            totalActivitiesSpan.textContent = `총 ${count}개 캠페인 활동`;
        })
        .catch((error) => {
            console.error('총 캠페인 활동 수 조회 오류:', error);
            totalActivitiesSpan.textContent = '총 캠페인 활동 수를 불러오지 못했습니다.';
        });

    fetch('/api/v1/campaign/activities')
        .then((response) => response.json())
        .then((data) => {
            activityTableBody.innerHTML = '';
            data.forEach((activity) => {
                const row = document.createElement('tr');
                const typeLabelMap = {
                    FIRST_COME_FIRST_SERVE: '선착순',
                    COUPON: '쿠폰',
                    GIVEAWAY: '경품'
                };
                const limit = activity.limitCount ?? ACTIVITY_PROGRESS_DEFAULT_LIMIT;
                const participantCount = activity.participantCount ?? 0;

                row.innerHTML = `
                    <td><label><input type="checkbox" class="check"></label></td>
                    <td><span class="link" data-activity-id="${activity.id}">${activity.name}</span></td>
                    <td><span class="badge"><span class="dot"></span> ${activity.status}</span></td>
                    <td>${typeLabelMap[activity.activityType] ?? activity.activityType}</td>
                    <td>${formatDate(activity.startDate)} ~ ${formatDate(activity.endDate)}</td>
                    <td>
                        <div class="progress">
                            <div class="bar" style="width:${resolveProgressWidth(participantCount, limit)}%"></div>
                        </div>
                        <span class="progress-text">${formatProgress(participantCount, limit)}</span>
                    </td>
                    <td>${formatDate(activity.createdAt)}</td>
                    <td>
                        <div class="action-menu-container">
                            <button class="action-menu-trigger" data-activity-id="${activity.id}" data-activity-status="${activity.status}">
                                <i class="fa-solid fa-ellipsis-vertical"></i>
                            </button>
                        </div>
                    </td>
                `;
                activityTableBody.appendChild(row);
            });
        })
        .catch((error) => {
            console.error('캠페인 활동 목록 조회 오류:', error);
            activityTableBody.innerHTML = '<tr><td colspan="8">목록 불러오기를 실패했습니다. 다시 시도해주세요.</td></tr>';
        });

    let activeDropdown = null;

    document.body.addEventListener('click', (event) => {
        const trigger = event.target.closest('.action-menu-trigger');
        const actionItem = event.target.closest('.action-menu-item');

        if (trigger) {
            event.stopPropagation();
            if (activeDropdown) activeDropdown.remove();

            const activityId = trigger.dataset.activityId;
            const currentStatus = trigger.dataset.activityStatus;
            const rect = trigger.getBoundingClientRect();

            const dropdown = document.createElement('div');
            dropdown.classList.add('action-menu-dropdown');
            let dropdownContent = `
                <a href="#" class="action-menu-item edit-activity" data-activity-id="${activityId}">수정</a>
                <a href="#" style="color: tomato" class="action-menu-item delete-activity" data-activity-id="${activityId}">삭제</a>
            `;

            if (currentStatus === 'DRAFT') {
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-activity-id="${activityId}" data-new-status="ACTIVE"><b>ACTIVE</b>로 상태 변경</a>`;
            } else if (currentStatus === 'ACTIVE') {
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-activity-id="${activityId}" data-new-status="PAUSED"><b>PAUSED</b>로 상태 변경</a>`;
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-activity-id="${activityId}" data-new-status="ENDED"><b>ENDED</b>로 상태 변경</a>`;
            } else if (currentStatus === 'PAUSED') {
                dropdownContent += `<a href="#" class="action-menu-item change-status" data-activity-id="${activityId}" data-new-status="ACTIVE"><b>ACTIVE</b>로 상태 변경</a>`;
            }

            dropdown.innerHTML = dropdownContent;
            dropdown.style.position = 'absolute';
            dropdown.style.top = `${rect.bottom + window.scrollY}px`;
            dropdown.style.left = `${rect.left + window.scrollX}px`;
            dropdown.style.zIndex = '1000';

            document.body.appendChild(dropdown);
            activeDropdown = dropdown;
            return;
        }

        if (actionItem) {
            const activityId = actionItem.dataset.activityId;
            const activityNameElement = document.querySelector(`tr .link[data-activity-id="${activityId}"]`);
            const activityName = activityNameElement ? activityNameElement.textContent : `캠페인 활동 ${activityId}`;

            if (actionItem.classList.contains('edit-activity')) {
                modalHandler.showEditModal(activityId, activityName);
            } else if (actionItem.classList.contains('delete-activity')) {
                modalHandler.showDeleteModal(activityId, activityName);
            } else if (actionItem.classList.contains('change-status')) {
                const newStatus = actionItem.dataset.newStatus;
                const currentStatus = document.querySelector(`.action-menu-trigger[data-activity-id="${activityId}"]`).dataset.activityStatus;
                modalHandler.showStatusModal(activityId, activityName, currentStatus, newStatus);
            }

            if (activeDropdown) activeDropdown.remove();
            activeDropdown = null;
            return;
        }

        if (activeDropdown) {
            activeDropdown.remove();
            activeDropdown = null;
        }
    });

    const createCampaignActivityBtn = document.getElementById('create-campaign-btn');
    if (createCampaignActivityBtn) {
        createCampaignActivityBtn.addEventListener('click', (event) => {
            event.preventDefault();
            modalHandler.showCreateCampaignModal?.();
        });
    }
});
