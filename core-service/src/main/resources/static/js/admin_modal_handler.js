const modalHandler = {
    showDeleteModal: function(campaignActivityId, campaignActivityName) {
        const existingModal = document.getElementById('delete-modal-backdrop');
        if (existingModal) existingModal.remove();

        const modalHtml = `
            <div id="delete-modal-backdrop" class="modal-backdrop">
                <div class="modal-content">
                    <div class="modal-header">
                        <h3 class="modal-title">캠페인 활동 삭제</h3>
                        <button class="modal-close-button">&times;</button>
                    </div>
                    <div class="modal-body">
                        <p><strong>'${campaignActivityName}'</strong> 캠페인 활동을 정말로 삭제하시겠습니까?</p>
                        <p>이 작업은 되돌릴 수 없습니다.</p>
                    </div>
                    <div class="modal-footer">
                        <button id="confirm-delete-btn" class="btn-danger">삭제</button>
                        <button id="cancel-delete-btn" class="btn-secondary">취소</button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHtml);

        const modalBackdrop = document.getElementById('delete-modal-backdrop');
        const confirmDeleteBtn = document.getElementById('confirm-delete-btn');
        const cancelDeleteBtn = document.getElementById('cancel-delete-btn');
        const closeButton = modalBackdrop.querySelector('.modal-close-button');

        const closeModal = () => modalBackdrop.remove();

        modalBackdrop.addEventListener('click', (e) => { if (e.target === modalBackdrop) closeModal(); });
        closeButton.addEventListener('click', closeModal);
        cancelDeleteBtn.addEventListener('click', closeModal);

        confirmDeleteBtn.addEventListener('click', async () => {
            const token = common.getCookie("accessToken");
            try {
                const res = await fetch(`/api/v1/campaign/activities/${campaignActivityId}`, {
                    method: 'DELETE',
                    headers: { "Authorization": `Bearer ${token}` },
                });
                if (!res.ok) {
                    const errorText = await res.text();
                    let errorMessage = '캠페인 활동 삭제에 실패했습니다.';
                    try { errorMessage = JSON.parse(errorText).message || errorMessage; } catch (e) { errorMessage = errorText || errorMessage; }
                    throw new Error(errorMessage);
                }
                alert('캠페인 활동이 성공적으로 삭제되었습니다!');
                closeModal();
                location.reload();
            } catch (error) {
                console.error('캠페인 활동 삭제 오류:', error);
                alert('캠페인 활동 삭제 중 오류가 발생했습니다: ' + error.message);
            }
        });
    },

    showEditModal: async function(campaignActivityId, campaignActivityName) {
        const existingModal = document.getElementById('edit-modal-backdrop');
        if (existingModal) existingModal.remove();

        const [activityDetails, campaigns] = await Promise.all([
            fetch(`/api/v1/campaign/activities/${campaignActivityId}`).then(res => res.ok ? res.json() : Promise.reject('캠페인 활동 정보를 불러오는데 실패했습니다.')),
            fetch('/api/v1/campaign').then(res => res.ok ? res.json() : Promise.reject('캠페인 목록을 불러오는데 실패했습니다.'))
        ]).catch(error => {
            console.error('Error fetching data for edit modal:', error);
            alert(error);
            return [null, null];
        });

        if (!activityDetails || !campaigns) return;

        const modalHtml = `
            <div id="edit-modal-backdrop" class="modal-backdrop">
                <div class="modal-content large-modal">
                    <div class="modal-header">
                        <h3 class="modal-title">캠페인 활동 수정 (<b>${campaignActivityName}</b>)</h3>
                        <button class="modal-close-button">&times;</button>
                    </div>
                    <div class="modal-body">
                        <form id="edit-activity-form" class="space-y-6">
                            <section class="section-card">
                                <h2 style="font-size: larger" class="section-title">캠페인 활동 기본 정보</h2><br>
                                <div class="space-y-6">
                                    <div>
                                        <label class="form-label">캠페인 활동 이름 *</label>
                                        <input id="editActivityName" type="text" class="form-input" placeholder="캠페인 활동명을 입력하세요" value="${activityDetails.name}" required>
                                    </div>
                                    <div>
                                        <label class="form-label">캠페인 선택 *</label>
                                        <select id="editCampaignSelect" class="form-input" required>
                                            <option value="">캠페인을 선택하세요</option>
                                            ${campaigns.map(campaign => `<option value="${campaign.id}" ${campaign.id === activityDetails.campaignId ? 'selected' : ''}>${campaign.name}</option>`).join('')}
                                        </select>
                                    </div>
                                    <br>
                                    <div>
                                        <label class="form-label">캠페인 활동 유형 *</label>
                                        <div id="editCampaignActivityTypeGroup" class="grid grid-cols-3 gap-4">
                                            <button type="button" class="campaign-type selectable ${activityDetails.activityType === 'FIRST_COME_FIRST_SERVE' ? 'selected' : ''}" data-type="FIRST_COME_FIRST_SERVE">
                                                <i class="fa-solid fa-clock type-icon"></i>
                                                <div>선착순</div>
                                                <div class="type-desc">먼저 참여한 순서대로</div>
                                            </button>
                                            <button type="button" class="campaign-type selectable ${activityDetails.activityType === 'COUPON' ? 'selected' : ''}" data-type="COUPON">
                                                <i class="fa-solid fa-check-circle type-icon"></i>
                                                <div>조건부</div>
                                                <div class="type-desc">특정 조건 달성 시</div>
                                            </button>
                                            <button type="button" class="campaign-type selectable ${activityDetails.activityType === 'GIVEAWAY' ? 'selected' : ''}" data-type="GIVEAWAY">
                                                <i class="fa-solid fa-gift type-icon"></i>
                                                <div>응모/추첨</div>
                                                <div class="type-desc">추첨을 통한 당첨</div>
                                            </button>
                                        </div>
                                        <input type="hidden" id="editCampaignActivityTypeInput" value="${activityDetails.activityType}" required>
                                    </div>
                                </div>
                            </section>
                            <br>
                            <section class="section-card">
                                <h2 style="font-size: larger" class="section-title">캠페인 활동 조건 설정</h2>
                                <div class="space-y-6">
                                    <div>
                                        <label class="form-label">참여 인원 수</label>
                                        <input id="editLimitCountInput" type="number" class="form-input" placeholder="최대 참여 인원을 입력하세요" value="${activityDetails.limitCount || ''}">
                                    </div>
                                    <div>
                                        <label class="form-label">필터 제한</label>
                                        <div class="space-y-4">
                                            <div class="filter-group">
                                                <label class="flex items-center">
                                                    <input type="checkbox" class="form-checkbox filter-type-checkbox" data-filter-type="AGE">
                                                    <span class="ml-2 font-medium">나이 제한</span>
                                                </label>
                                                <div class="filter-details hidden ml-6 mt-2 space-y-2" data-filter-details="AGE">
                                                    <div id="edit-age-filter-inputs"></div>
                                                    <button type="button" id="edit-add-age-range-btn" class="btn-secondary text-sm py-1 px-2">+ 범위 추가</button>
                                                </div>
                                            </div>
                                            <div class="filter-group">
                                                <label class="flex items-center">
                                                    <input type="checkbox" class="form-checkbox filter-type-checkbox" data-filter-type="REGION">
                                                    <span class="ml-2 font-medium">지역 제한</span>
                                                </label>
                                                <div class="filter-details hidden ml-6 mt-2 space-y-2" data-filter-details="REGION">
                                                    <div class="flex items-center gap-2">
                                                        <select id="edit-region-sido-select" class="form-input flex-grow">
                                                            <option value="">시/도 선택</option>
                                                        </select>
                                                        <select id="edit-region-sigungu-select" class="form-input flex-grow">
                                                            <option value="">시/군/구 선택</option>
                                                        </select>
                                                    </div>
                                                    <div id="edit-selected-regions-container" class="mt-2"></div>
                                                </div>
                                            </div>
                                            <div class="filter-group">
                                                <label class="flex items-center">
                                                    <input type="checkbox" class="form-checkbox filter-type-checkbox" data-filter-type="VIP_TIER">
                                                    <span class="ml-2 font-medium">VIP 등급 제한</span>
                                                </label>
                                                <div class="filter-details hidden ml-6 mt-2 space-y-2" data-filter-details="VIP_TIER">
                                                    <label class="flex items-center"><input type="checkbox" class="form-checkbox filter-value-checkbox" value="BRONZE"><span class="ml-2">브론즈</span></label>
                                                    <label class="flex items-center"><input type="checkbox" class="form-checkbox filter-value-checkbox" value="SILVER"><span class="ml-2">실버</span></label>
                                                    <label class="flex items-center"><input type="checkbox" class="form-checkbox filter-value-checkbox" value="GOLD"><span class="ml-2">골드</span></label>
                                                    <label class="flex items-center"><input type="checkbox" class="form-checkbox filter-value-checkbox" value="PLATINUM"><span class="ml-2">플레티넘</span></label>
                                                    <label class="flex items-center"><input type="checkbox" class="form-checkbox filter-value-checkbox" value="DIAMOND"><span class="ml-2">다이아몬드</span></label>
                                                    <label class="flex items-center"><input type="checkbox" class="form-checkbox filter-value-checkbox" value="VIP"><span class="ml-2">VIP</span></label>
                                                    <label class="flex items-center"><input type="checkbox" class="form-checkbox filter-value-checkbox" value="VVIP"><span class="ml-2">VVIP</span></label>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </section>
                            <section class="section-card">
                                <h2 style="font-size: larger" class="section-title">일정 설정</h2><br>
                                <div class="space-y-6">
                                    <div class="grid grid-cols-2 gap-4">
                                        <div>
                                            <label class="form-label">시작일 *</label>
                                            <input id="editStartDate" type="datetime-local" class="form-input" value="${activityDetails.startDate ? activityDetails.startDate.substring(0, 16) : ''}" required>
                                        </div>
                                        <div>
                                            <label class="form-label">종료일 *</label>
                                            <input id="editEndDate" type="datetime-local" class="form-input" value="${activityDetails.endDate ? activityDetails.endDate.substring(0, 16) : ''}" required>
                                        </div>
                                    </div>
                                </div>
                            </section>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="save-edit-btn" class="btn-primary">저장</button>
                        <button id="cancel-edit-btn" class="btn-secondary">취소</button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHtml);

        const modalBackdrop = document.getElementById('edit-modal-backdrop');
        const closeModal = () => modalBackdrop.remove();
        modalBackdrop.querySelector('.modal-close-button').addEventListener('click', closeModal);
        document.getElementById('cancel-edit-btn').addEventListener('click', closeModal);
        const modalContent = modalBackdrop.querySelector('.modal-content');
        if (modalContent) {
            modalContent.style.maxHeight = '85vh';
            modalContent.style.overflowY = 'auto';
        }

        document.getElementById('editCampaignActivityTypeGroup').querySelectorAll('.selectable').forEach(button => {
            button.addEventListener('click', () => {
                document.getElementById('editCampaignActivityTypeGroup').querySelectorAll('.selectable').forEach(btn => btn.classList.remove('selected'));
                button.classList.add('selected');
                document.getElementById('editCampaignActivityTypeInput').value = button.dataset.type;
            });
        });

        const filterTypeCheckboxes = modalBackdrop.querySelectorAll('.filter-type-checkbox');
        const ageInputsContainer = modalBackdrop.querySelector('#edit-age-filter-inputs');
        const addAgeRangeBtn = modalBackdrop.querySelector('#edit-add-age-range-btn');
        const sidoSelect = modalBackdrop.querySelector('#edit-region-sido-select');
        const sigunguSelect = modalBackdrop.querySelector('#edit-region-sigungu-select');
        const selectedRegionsContainer = modalBackdrop.querySelector('#edit-selected-regions-container');

        const createAgeRangeRow = (start = '', end = '') => {
            const wrapper = document.createElement('div');
            wrapper.className = 'edit-age-range-row flex items-center gap-2 mb-2';
            wrapper.innerHTML = `
                <input type="number" placeholder="20" class="form-input age-range-start w-20">
                <span>~</span>
                <input type="number" placeholder="29" class="form-input age-range-end w-20">
                <button type="button" class="remove-age-range-btn text-red-500">-</button>
            `;
            wrapper.querySelector('.age-range-start').value = start ?? '';
            wrapper.querySelector('.age-range-end').value = end ?? '';
            return wrapper;
        };

        const ensureAgeRows = () => {
            if (!ageInputsContainer) return;
            const ageCheckbox = modalBackdrop.querySelector('.filter-type-checkbox[data-filter-type="AGE"]');
            if (ageCheckbox && !ageCheckbox.checked) return;
            if (!ageInputsContainer.children.length) {
                ageInputsContainer.appendChild(createAgeRangeRow());
            }
        };

        const toggleDetails = (checkbox, forceShow = false) => {
            const details = modalBackdrop.querySelector(`.filter-details[data-filter-details="${checkbox.dataset.filterType}"]`);
            if (!details) return;
            const shouldShow = forceShow || checkbox.checked;
            details.classList.toggle('hidden', !shouldShow);
            if (!shouldShow) {
                if (checkbox.dataset.filterType === 'AGE' && ageInputsContainer) {
                    ageInputsContainer.innerHTML = '';
                }
                if (checkbox.dataset.filterType === 'REGION' && selectedRegionsContainer) {
                    selectedRegionsContainer.innerHTML = '';
                }
                if (checkbox.dataset.filterType !== 'AGE' && checkbox.dataset.filterType !== 'REGION') {
                    details.querySelectorAll('.filter-value-checkbox').forEach(cb => cb.checked = false);
                }
            } else if (checkbox.dataset.filterType === 'AGE') {
                ensureAgeRows();
            }
        };

        filterTypeCheckboxes.forEach(checkbox => {
            checkbox.addEventListener('change', () => toggleDetails(checkbox));
        });

        if (ageInputsContainer && addAgeRangeBtn) {
            addAgeRangeBtn.addEventListener('click', () => {
                if (ageInputsContainer.children.length >= 10) return;
                ageInputsContainer.appendChild(createAgeRangeRow());
            });
            ageInputsContainer.addEventListener('click', (event) => {
                if (event.target.classList.contains('remove-age-range-btn')) {
                    event.target.parentElement.remove();
                    ensureAgeRows();
                }
            });
        }

        const addRegionTag = (value) => {
            if (!selectedRegionsContainer || !value) return;
            if (selectedRegionsContainer.querySelector(`[data-region="${value}"]`)) {
                alert('이미 추가된 지역입니다.');
                return;
            }
            const tag = document.createElement('span');
            tag.className = 'selected-region-tag bg-gray-200 text-gray-800 text-sm font-medium mr-2 px-2.5 py-0.5 rounded';
            tag.dataset.region = value;
            const label = value.includes('-') ? value.replace('-', ' ') : `${value} 전체`;
            tag.textContent = label;
            const removeBtn = document.createElement('button');
            removeBtn.type = 'button';
            removeBtn.className = 'remove-region-btn ml-2 text-red-500';
            removeBtn.innerHTML = '&times;';
            removeBtn.addEventListener('click', () => tag.remove());
            tag.appendChild(removeBtn);
            selectedRegionsContainer.appendChild(tag);
        };

        if (selectedRegionsContainer) {
            selectedRegionsContainer.addEventListener('click', (event) => {
                if (event.target.classList.contains('remove-region-btn')) {
                    event.target.parentElement.remove();
                }
            });
        }

        let districtData = {};

        const populateSidoOptions = () => {
            if (!sidoSelect) return;
            sidoSelect.innerHTML = '<option value="">시/도 선택</option>';
            Object.keys(districtData).forEach(sido => {
                sidoSelect.add(new Option(sido, sido));
            });
        };

        const populateSigunguOptions = (sido) => {
            if (!sigunguSelect) return;
            sigunguSelect.innerHTML = '<option value="">시/군/구 선택</option>';
            if (!sido || !districtData[sido]) return;
            sigunguSelect.add(new Option('전체', 'ALL'));
            districtData[sido].forEach(sigungu => sigunguSelect.add(new Option(sigungu, sigungu)));
        };

        if (sidoSelect && sigunguSelect) {
            sidoSelect.addEventListener('change', () => {
                populateSigunguOptions(sidoSelect.value);
                sigunguSelect.value = '';
            });

            sigunguSelect.addEventListener('change', () => {
                const sido = sidoSelect.value;
                const sigungu = sigunguSelect.value;
                if (!sido || !sigungu) return;
                const value = sigungu === 'ALL' ? sido : `${sido}-${sigungu}`;
                addRegionTag(value);
                sigunguSelect.value = '';
            });
        }

        if (sidoSelect) {
            fetch('/data/korean-districts.json')
                .then(res => res.json())
                .then(data => {
                    districtData = data || {};
                    populateSidoOptions();
                })
                .catch(error => console.error('지역 데이터를 불러오지 못했습니다.', error));
        }

        const existingFilters = Array.isArray(activityDetails.filters) ? activityDetails.filters : [];
        existingFilters.forEach(filter => {
            const checkbox = modalBackdrop.querySelector(`.filter-type-checkbox[data-filter-type="${filter.type}"]`);
            if (!checkbox) return;
            checkbox.checked = true;
            toggleDetails(checkbox, true);

            if (filter.type === 'AGE' && ageInputsContainer) {
                ageInputsContainer.innerHTML = '';
                (filter.values || []).forEach(value => {
                    const [start, end] = String(value || '').split('-');
                    ageInputsContainer.appendChild(createAgeRangeRow(start, end));
                });
                ensureAgeRows();
            } else if (filter.type === 'REGION' && selectedRegionsContainer) {
                selectedRegionsContainer.innerHTML = '';
                (filter.values || []).forEach(value => addRegionTag(value));
            } else {
                (filter.values || []).forEach(value => {
                    const input = modalBackdrop.querySelector(`.filter-details[data-filter-details="${filter.type}"] .filter-value-checkbox[value="${value}"]`);
                    if (input) input.checked = true;
                });
            }
        });
        ensureAgeRows();

        document.getElementById('save-edit-btn').addEventListener('click', async () => {
            const form = document.getElementById('edit-activity-form');
            const limitcount = document.getElementById("editLimitCountInput").value;
            if (!form.checkValidity()) { form.reportValidity(); return; }
            if(!limitcount || limitcount <= 0) {alert("최소 참가 인원은 1명입니다."); return;}

            const nowtime = new Date();
            const start = new Date(document.getElementById("editStartDate").value);
            const end = new Date(document.getElementById("editEndDate").value);

            if (start <= nowtime) { alert("시작 시간은 현재 시간보다 미래여야 합니다."); return; }
            if (end <= nowtime) { alert("종료 시간은 현재 시간보다 미래여야 합니다."); return; }
            if (end <= start) { alert("종료 시간은 시작 시간보다 미래여야 합니다."); return; }

            const filters = [];
            let hasFilterValidationError = false;

            modalBackdrop.querySelectorAll('.filter-type-checkbox:checked').forEach(checkbox => {
                if (hasFilterValidationError) return;
                const type = checkbox.dataset.filterType;
                let values = [];

                if (type === 'AGE' && ageInputsContainer) {
                    const rows = Array.from(ageInputsContainer.querySelectorAll('.edit-age-range-row'));
                    if (!rows.length) {
                        alert('나이 범위를 추가해주세요.');
                        hasFilterValidationError = true;
                        return;
                    }
                    for (const row of rows) {
                        const startVal = row.querySelector('.age-range-start').value;
                        const endVal = row.querySelector('.age-range-end').value;
                        if (!startVal || !endVal) {
                            alert('나이 범위를 모두 입력해주세요.');
                            hasFilterValidationError = true;
                            break;
                        }
                        if (Number(startVal) < 0 || Number(endVal) < 0) {
                            alert('나이는 0 이상이어야 합니다.');
                            hasFilterValidationError = true;
                            break;
                        }
                        if (Number(endVal) < Number(startVal)) {
                            alert('끝 나이는 시작 나이보다 크거나 같아야 합니다.');
                            hasFilterValidationError = true;
                            break;
                        }
                        values.push(`${startVal}-${endVal}`);
                    }
                } else if (type === 'REGION' && selectedRegionsContainer) {
                    const regionTags = selectedRegionsContainer.querySelectorAll('.selected-region-tag');
                    if (!regionTags.length) {
                        alert('지역 필터를 선택했지만 지역을 추가하지 않았습니다.');
                        hasFilterValidationError = true;
                        return;
                    }
                    regionTags.forEach(tag => values.push(tag.dataset.region));
                } else {
                    const checked = modalBackdrop.querySelectorAll(`.filter-details[data-filter-details="${type}"] .filter-value-checkbox:checked`);
                    if (!checked.length) {
                        alert('선택한 필터에 대한 값을 하나 이상 선택하세요.');
                        hasFilterValidationError = true;
                        return;
                    }
                    values = Array.from(checked).map(cb => cb.value);
                }

                if (!hasFilterValidationError && values.length) {
                    filters.push({ type, values });
                }
            });

            if (hasFilterValidationError) {
                return;
            }

            const payload = {
                name: document.getElementById('editActivityName').value,
                limitCount: parseInt(document.getElementById('editLimitCountInput').value) || 0,
                status: activityDetails.status,
                startDate: document.getElementById('editStartDate').value + ':00',
                endDate: document.getElementById('editEndDate').value + ':00',
                activityType: document.getElementById('editCampaignActivityTypeInput').value,
                filters
            };

            const token = common.getCookie("accessToken");
            try {
                const res = await fetch(`/api/v1/campaign/activities/${campaignActivityId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
                    body: JSON.stringify(payload)
                });
                if (!res.ok) {
                    const errorData = await res.json().catch(() => ({ message: '캠페인 활동 수정에 실패했습니다.' }));
                    throw new Error(errorData.message);
                }
                alert('캠페인 활동이 성공적으로 수정되었습니다!');
                closeModal();
                location.reload();
            } catch (error) {
                console.error('캠페인 활동 수정 오류:', error);
                alert('캠페인 활동 수정 중 오류가 발생했습니다: ' + error.message);
            }
        });
    },

    showStatusModal: function(campaignActivityId, campaignActivityName, currentStatus, newStatus) {
        const existingModal = document.getElementById('status-modal-backdrop');
        if (existingModal) existingModal.remove();

        const modalHtml = `
            <div id="status-modal-backdrop" class="modal-backdrop">
                <div class="modal-content">
                    <div class="modal-header">
                        <h3 class="modal-title">캠페인 활동 상태 변경 확인</h3>
                        <button class="modal-close-button">&times;</button>
                    </div>
                    <div class="modal-body">
                        <p><strong>'${campaignActivityName}'</strong> 캠페인 활동의 상태를</p>
                        <p><strong>${currentStatus}</strong> 에서 <strong style="color: tomato">${newStatus}</strong> (으)로 변경하시겠습니까?</p>
                        <br>
                        <div id="ENDED-message"></div>
                    </div>
                    <div class="modal-footer">
                        <button id="confirm-status-change-btn" class="btn-primary">변경</button>
                        <button id="cancel-status-change-btn" class="btn-secondary">취소</button>
                    </div>
                </div>
            </div>
        `;
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        if(currentStatus === "ACTIVE" && newStatus === "ENDED") {
            document.getElementById("ENDED-message").innerHTML = "<strong style='color: tomato'>ENDED</strong>상태로 변경시 캠페인 활동이 종료되며,<br> 더이상 상태변경이 불가능해집니다.";
        }

        const modalBackdrop = document.getElementById('status-modal-backdrop');
        const confirmBtn = document.getElementById('confirm-status-change-btn');
        const cancelBtn = document.getElementById('cancel-status-change-btn');
        const closeButton = modalBackdrop.querySelector('.modal-close-button');

        const closeModal = () => modalBackdrop.remove();

        modalBackdrop.addEventListener('click', (e) => { if (e.target === modalBackdrop) closeModal(); });
        closeButton.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);

        confirmBtn.addEventListener('click', async () => {
            const token = common.getCookie("accessToken");
            try {
                const res = await fetch(`/api/v1/campaign/activities/${campaignActivityId}/status?status=${newStatus}`, {
                    method: 'PATCH',
                    headers: { 'Authorization': `Bearer ${token}` },
                });
                if (!res.ok) {
                    const errorData = await res.json().catch(() => ({ message: '상태 변경에 실패했습니다.' }));
                    throw new Error(errorData.message);
                }
                alert(`캠페인 활동 상태가 ${newStatus}(으)로 성공적으로 변경되었습니다!`);
                closeModal();
                location.reload();
            } catch (error) {
                console.error('캠페인 활동 상태 변경 오류:', error);
                alert('캠페인 활동 상태 변경 중 오류가 발생했습니다: ' + error.message);
            }
        });
    },

    showCreateCampaignModal: function() {
        const existingModal = document.getElementById('create-campaign-modal-backdrop');
        if (existingModal) existingModal.remove();

        const modalHtml = `
            <div id="create-campaign-modal-backdrop" class="modal-backdrop">
                <div class="modal-content">
                    <div class="modal-header">
                        <h3 class="modal-title">새 캠페인 생성</h3>
                        <button class="modal-close-button">&times;</button>
                    </div>
                    <div class="modal-body">
                        <form id="create-campaign-form">
                            <label for="new-campaign-name" class="form-label">캠페인 이름 *</label>
                            <div class="flex items-center gap-2">
                                <input type="text" id="new-campaign-name" class="form-input" required>
                                <button type="button" id="check-duplicate-btn" class="btn-secondary">중복 확인</button>
                            </div>
                            <p id="duplicate-check-result" class="text-sm mt-2"></p>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="confirm-create-campaign-btn" class="btn-primary" disabled>생성</button>
                        <button id="cancel-create-campaign-btn" class="btn-secondary">취소</button>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHtml);

        const modalBackdrop = document.getElementById('create-campaign-modal-backdrop');
        const closeButton = modalBackdrop.querySelector('.modal-close-button');
        const cancelBtn = document.getElementById('cancel-create-campaign-btn');
        const checkDuplicateBtn = document.getElementById('check-duplicate-btn');
        const confirmBtn = document.getElementById('confirm-create-campaign-btn');
        const campaignNameInput = document.getElementById('new-campaign-name');
        const duplicateCheckResult = document.getElementById('duplicate-check-result');

        const closeModal = () => modalBackdrop.remove();

        closeButton.addEventListener('click', closeModal);
        cancelBtn.addEventListener('click', closeModal);
        modalBackdrop.addEventListener('click', (e) => { if (e.target === modalBackdrop) closeModal(); });

        let isNameAvailable = false;

        campaignNameInput.addEventListener('input', () => {
            isNameAvailable = false;
            confirmBtn.disabled = true;
            duplicateCheckResult.textContent = '';
        });

        checkDuplicateBtn.addEventListener('click', async () => {
            const name = campaignNameInput.value.trim();
            if (!name) { alert('캠페인 이름을 입력하세요.'); return; }

            try {
                const res = await fetch(`/api/v1/campaign/exists?name=${encodeURIComponent(name)}`);
                const isTaken = await res.json();

                if (isTaken) {
                    duplicateCheckResult.textContent = '이미 사용 중인 이름입니다.';
                    duplicateCheckResult.style.color = 'red';
                    isNameAvailable = false;
                    confirmBtn.disabled = true;
                } else {
                    duplicateCheckResult.textContent = '사용 가능한 이름입니다.';
                    duplicateCheckResult.style.color = 'green';
                    isNameAvailable = true;
                    confirmBtn.disabled = false;
                }
            } catch (error) {
                console.error('중복 확인 오류:', error);
                alert('중복 확인 중 오류가 발생했습니다.');
            }
        });

        confirmBtn.addEventListener('click', async () => {
            if (!isNameAvailable) { alert('캠페인 이름 중복 확인을 먼저 수행하세요.'); return; }

            const name = campaignNameInput.value.trim();
            const payload = { name: name };
            const token = common.getCookie("accessToken");

            try {
                const res = await fetch('/api/v1/campaign', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
                    body: JSON.stringify(payload)
                });

                if (!res.ok) {
                    const errorData = await res.json().catch(() => ({ message: '캠페인 생성에 실패했습니다.' }));
                    throw new Error(errorData.message);
                }

                alert('새 캠페인이 성공적으로 생성되었습니다!');
                closeModal();
                location.reload();
            } catch (error) {
                console.error('캠페인 생성 오류:', error);
                alert('캠페인 생성 중 오류가 발생했습니다: ' + error.message);
            }

        });
    }
};
