document.addEventListener('DOMContentLoaded', () => {
    const campaignListContainer = document.getElementById('campaign-list-container');
    const campaignDetailHeader = document.getElementById('campaign-detail-header');
    const activityContent = document.getElementById('activity-content');
    const noCampaignSelected = document.getElementById('no-campaign-selected');
    const activityTableBody = document.getElementById('activity-table-body');

    // Detail Header Elements
    const selectedCampaignName = document.getElementById('selected-campaign-name');
    const selectedCampaignDate = document.getElementById('selected-campaign-date');
    const selectedCampaignStatus = document.getElementById('selected-campaign-status');
    const statTotalActivities = document.getElementById('stat-total-activities');
    const statActiveActivities = document.getElementById('stat-active-activities');
    const statTotalParticipants = document.getElementById('stat-total-participants');

    let campaignsData = [];
    let selectedCampaignId = null;
    let activeDropdown = null;

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (activeDropdown && !activeDropdown.contains(e.target)) {
            activeDropdown.remove();
            activeDropdown = null;
        }
    });

    // Initial Fetch
    fetchCampaigns();

    // Event Listeners
    const createCampaignBtn = document.getElementById('create-campaign-btn');
    if (createCampaignBtn) {
        createCampaignBtn.addEventListener('click', () => {
            if (window.adminModalHandler && window.adminModalHandler.showCreateCampaignModal) {
                window.adminModalHandler.showCreateCampaignModal();
            } else {
                console.error('adminModalHandler not found or showCreateCampaignModal not available');
            }
        });
    }

    const createActivityBtn = document.getElementById('create-activity-btn');
    if (createActivityBtn) {
        createActivityBtn.addEventListener('click', () => {
            if (selectedCampaignId) {
                // Check if adminModalHandler has a specific function for creating activities
                if (window.adminModalHandler && window.adminModalHandler.showCreateActivityModal) {
                    window.adminModalHandler.showCreateActivityModal(selectedCampaignId);
                } else {
                    // Fallback to navigating to the create activity page with campaign ID
                    window.location.href = `/admin_create_campaignActivitys?campaignId=${selectedCampaignId}`;
                }
            } else {
                alert('캠페인을 먼저 선택해주세요.');
            }
        });
    }

    function fetchCampaigns() {
        fetch('/api/v1/campaign')
            .then((response) => response.json())
            .then((data) => {
                campaignsData = data;
                renderCampaignList(data);

                // Select first campaign by default if available
                if (data.length > 0) {
                    selectCampaign(data[0].id);
                } else {
                    // If no campaigns, ensure no campaign selected state is shown
                    if (noCampaignSelected) noCampaignSelected.classList.remove('hidden');
                    if (campaignDetailHeader) campaignDetailHeader.classList.add('hidden');
                    if (activityContent) activityContent.classList.add('hidden');
                }
            })
            .catch((error) => {
                console.error('캠페인 목록 조회 오류:', error);
                if (campaignListContainer) campaignListContainer.innerHTML = '<div class="text-center py-4 text-red-500">목록을 불러오지 못했습니다.</div>';
                if (noCampaignSelected) noCampaignSelected.classList.remove('hidden');
                if (campaignDetailHeader) campaignDetailHeader.classList.add('hidden');
                if (activityContent) activityContent.classList.add('hidden');
            });
    }

    function renderCampaignList(campaigns) {
        if (!campaignListContainer) return;
        campaignListContainer.innerHTML = '';

        if (campaigns.length === 0) {
            campaignListContainer.innerHTML = '<div class="text-center py-10 text-gray-400 text-sm">등록된 캠페인이 없습니다.</div>';
            return;
        }

        campaigns.forEach(campaign => {
            const item = document.createElement('div');
            // Use a temporary class for selection that will be updated by selectCampaign
            item.className = `campaign-item p-4 border-b border-gray-100 cursor-pointer hover:bg-gray-50 transition-colors border-l-4 ${selectedCampaignId === campaign.id ? 'border-blue-600 bg-blue-50' : 'border-transparent'}`;
            item.dataset.id = campaign.id;

            // Determine status based on dates
            const now = new Date();
            const start = new Date(campaign.startAt);
            const end = new Date(campaign.endAt);

            let statusText = '예정';
            let statusClass = 'text-blue-600 bg-blue-100';

            if (now >= start && now <= end) {
                statusText = '진행중';
                statusClass = 'text-green-600 bg-green-100';
            } else if (now > end) {
                statusText = '종료';
                statusClass = 'text-gray-600 bg-gray-100';
            }

            item.innerHTML = `
                <div class="font-bold text-gray-900 mb-1 truncate">${campaign.name}</div>
                <div class="text-xs text-gray-500 flex justify-between items-center">
                    <span>${formatDateShort(campaign.startAt)} ~ ${formatDateShort(campaign.endAt)}</span>
                    <span class="px-2 py-0.5 rounded ${statusClass}">${statusText}</span>
                </div>
            `;

            item.addEventListener('click', () => selectCampaign(campaign.id));
            campaignListContainer.appendChild(item);
        });
    }

    function selectCampaign(id) {
        selectedCampaignId = id;
        const campaign = campaignsData.find(c => c.id === id);
        if (!campaign) return;

        // Update Sidebar Selection UI
        document.querySelectorAll('.campaign-item').forEach(el => {
            if (parseInt(el.dataset.id) === id) {
                el.classList.add('bg-blue-50', 'border-blue-600');
                el.classList.remove('border-transparent');
            } else {
                el.classList.remove('bg-blue-50', 'border-blue-600');
                el.classList.add('border-transparent');
            }
        });

        // Show Content Area
        if (noCampaignSelected) noCampaignSelected.classList.add('hidden');
        if (campaignDetailHeader) campaignDetailHeader.classList.remove('hidden');
        if (activityContent) activityContent.classList.remove('hidden');

        // Update Header Info
        if (selectedCampaignName) selectedCampaignName.textContent = campaign.name;
        if (selectedCampaignDate) selectedCampaignDate.innerHTML = `<i class="fa-regular fa-calendar mr-1"></i> ${formatDate(campaign.startAt)} ~ ${formatDate(campaign.endAt)}`;

        // Update Status Badge
        if (selectedCampaignStatus) {
            const now = new Date();
            const start = new Date(campaign.startAt);
            const end = new Date(campaign.endAt);
            let statusText = '예정';
            let statusClass = 'text-blue-600 bg-blue-100';

            if (now >= start && now <= end) {
                statusText = '진행중';
                statusClass = 'text-green-600 bg-green-100';
            } else if (now > end) {
                statusText = '종료';
                statusClass = 'text-gray-600 bg-gray-100';
            }
            selectedCampaignStatus.textContent = statusText;
            selectedCampaignStatus.className = `px-2 py-0.5 rounded text-xs ${statusClass}`;
        }

        // Calculate Stats
        const activities = campaign.campaignActivities || [];
        const totalActivities = activities.length;
        const activeActivities = activities.filter(a => a.status === 'ACTIVE').length; // Assuming status field exists
        const totalParticipants = activities.reduce((sum, a) => sum + (a.participantCount || 0), 0);

        if (statTotalActivities) statTotalActivities.textContent = totalActivities;
        if (statActiveActivities) statActiveActivities.textContent = activeActivities;
        if (statTotalParticipants) statTotalParticipants.textContent = totalParticipants.toLocaleString();

        // Render Activities
        renderActivityTable(activities);
    }

    function renderActivityTable(activities) {
        if (!activityTableBody) return;
        activityTableBody.innerHTML = '';

        if (activities.length === 0) {
            activityTableBody.innerHTML = `
                <tr>
                    <td colspan="8" class="text-center py-12 text-gray-400">
                        <i class="fa-regular fa-folder-open text-2xl mb-2 block"></i>
                        등록된 활동이 없습니다.
                    </td>
                </tr>
            `;
            return;
        }

        activities.forEach(activity => {
            const row = document.createElement('tr');
            row.className = 'hover:bg-gray-50 transition-colors border-b border-gray-100 last:border-0';

            const typeLabelMap = {
                FIRST_COME_FIRST_SERVE: { label: '선착순', color: 'text-blue-600 bg-blue-50' },
                COUPON: { label: '쿠폰', color: 'text-green-600 bg-green-50' },
                GIVEAWAY: { label: '경품', color: 'text-purple-600 bg-purple-50' }
            };
            const typeInfo = typeLabelMap[activity.activityType] || { label: activity.activityType, color: 'text-gray-600 bg-gray-50' };

            const limit = activity.limitCount ?? 100;
            const participantCount = activity.participantCount ?? 0;
            const progressPercent = Math.min(100, Math.round((participantCount / limit) * 100));

            row.innerHTML = `
                <td class="py-3 px-4 font-medium text-gray-900">${activity.name}</td>
                <td class="py-3 px-4">
                    <span class="px-2 py-1 text-xs font-semibold rounded-full ${activity.status === 'ACTIVE' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}">
                        <span class="dot inline-block w-1.5 h-1.5 rounded-full mr-1 ${activity.status === 'ACTIVE' ? 'bg-green-500' : 'bg-gray-500'}"></span>
                        ${activity.status}
                    </span>
                </td>
                <td class="py-3 px-4"><span class="text-xs px-2 py-1 rounded ${typeInfo.color}">${typeInfo.label}</span></td>
                <td class="py-3 px-4 text-gray-500">${formatDate(activity.startDate)} ~ ${formatDate(activity.endDate)}</td>
                <td class="py-3 px-4">
                    <div class="flex items-center gap-2">
                        <div class="w-16 h-1.5 bg-gray-200 rounded-full overflow-hidden">
                            <div class="h-full bg-black rounded-full" style="width:${progressPercent}%"></div>
                        </div>
                        <span class="text-xs text-gray-500">${progressPercent}%</span>
                    </div>
                </td>
                <td class="py-3 px-4 text-gray-500">${formatDate(activity.createdAt)}</td>
                <td class="py-3 px-4">
                    <button class="action-menu-trigger text-gray-400 hover:text-gray-600 relative" data-activity-id="${activity.id}" data-activity-name="${activity.name}" data-activity-status="${activity.status}">
                        <i class="fa-solid fa-ellipsis-vertical pointer-events-none"></i>
                    </button>
                </td>
            `;
            activityTableBody.appendChild(row);
        });

        // Add event listeners for action menu triggers
        document.querySelectorAll('.action-menu-trigger').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const activityId = btn.dataset.activityId;
                const activityName = btn.dataset.activityName;
                const currentStatus = btn.dataset.activityStatus;

                // Remove existing dropdown if any
                if (activeDropdown) {
                    activeDropdown.remove();
                    activeDropdown = null;
                }

                // Create Dropdown
                const dropdown = document.createElement('div');
                dropdown.className = 'fixed bg-white rounded-md shadow-lg z-[9999] border border-gray-200 py-1 text-sm w-32';

                // Calculate position
                const rect = btn.getBoundingClientRect();
                dropdown.style.top = `${rect.bottom + window.scrollY + 5}px`;
                dropdown.style.left = `${rect.right + window.scrollX - 128}px`; // Align right edge

                // Edit Option
                const editBtn = document.createElement('button');
                editBtn.className = 'block w-full text-left px-4 py-2 text-gray-700 hover:bg-gray-100';
                editBtn.innerHTML = '<i class="fa-regular fa-pen-to-square mr-2"></i> 수정';
                editBtn.onclick = () => {
                    if (window.adminModalHandler && window.adminModalHandler.showEditModal) {
                        window.adminModalHandler.showEditModal(activityId, activityName);
                    }
                    dropdown.remove();
                    activeDropdown = null;
                };
                dropdown.appendChild(editBtn);

                // Status Change Options
                if (currentStatus === 'DRAFT') {
                    const publishBtn = document.createElement('button');
                    publishBtn.className = 'block w-full text-left px-4 py-2 text-blue-600 hover:bg-gray-100';
                    publishBtn.innerHTML = '<i class="fa-solid fa-upload mr-2"></i> 게시';
                    publishBtn.onclick = () => {
                        if (window.adminModalHandler && window.adminModalHandler.showStatusModal) {
                            window.adminModalHandler.showStatusModal(activityId, activityName, currentStatus, 'ACTIVE');
                        }
                        dropdown.remove();
                        activeDropdown = null;
                    };
                    dropdown.appendChild(publishBtn);
                } else if (currentStatus === 'ACTIVE') {
                    const pauseBtn = document.createElement('button');
                    pauseBtn.className = 'block w-full text-left px-4 py-2 text-yellow-600 hover:bg-gray-100';
                    pauseBtn.innerHTML = '<i class="fa-solid fa-pause mr-2"></i> 일시정지';
                    pauseBtn.onclick = () => {
                        if (window.adminModalHandler && window.adminModalHandler.showStatusModal) {
                            window.adminModalHandler.showStatusModal(activityId, activityName, currentStatus, 'PAUSED');
                        }
                        dropdown.remove();
                        activeDropdown = null;
                    };
                    dropdown.appendChild(pauseBtn);

                    const endBtn = document.createElement('button');
                    endBtn.className = 'block w-full text-left px-4 py-2 text-red-600 hover:bg-gray-100';
                    endBtn.innerHTML = '<i class="fa-solid fa-stop mr-2"></i> 종료';
                    endBtn.onclick = () => {
                        if (window.adminModalHandler && window.adminModalHandler.showStatusModal) {
                            window.adminModalHandler.showStatusModal(activityId, activityName, currentStatus, 'ENDED');
                        }
                        dropdown.remove();
                        activeDropdown = null;
                    };
                    dropdown.appendChild(endBtn);
                } else if (currentStatus === 'PAUSED') {
                    const resumeBtn = document.createElement('button');
                    resumeBtn.className = 'block w-full text-left px-4 py-2 text-green-600 hover:bg-gray-100';
                    resumeBtn.innerHTML = '<i class="fa-solid fa-play mr-2"></i> 재개';
                    resumeBtn.onclick = () => {
                        if (window.adminModalHandler && window.adminModalHandler.showStatusModal) {
                            window.adminModalHandler.showStatusModal(activityId, activityName, currentStatus, 'ACTIVE');
                        }
                        dropdown.remove();
                        activeDropdown = null;
                    };
                    dropdown.appendChild(resumeBtn);
                }

                // Delete Option
                const deleteBtn = document.createElement('button');
                deleteBtn.className = 'block w-full text-left px-4 py-2 text-red-600 hover:bg-gray-100 border-t border-gray-100';
                deleteBtn.innerHTML = '<i class="fa-regular fa-trash-can mr-2"></i> 삭제';
                deleteBtn.onclick = () => {
                    if (window.adminModalHandler && window.adminModalHandler.showDeleteModal) {
                        window.adminModalHandler.showDeleteModal(activityId, activityName);
                    }
                    dropdown.remove();
                    activeDropdown = null;
                };
                dropdown.appendChild(deleteBtn);

                document.body.appendChild(dropdown);
                activeDropdown = dropdown;
            });
        });
    }

    function formatDate(dateString) {
        if (!dateString) return '-';
        const date = new Date(dateString);
        return date.toISOString().split('T')[0];
    }

    function formatDateShort(dateString) {
        if (!dateString) return '-';
        const date = new Date(dateString);
        return `${date.getMonth() + 1}.${date.getDate()}`;
    }
});
