window.adminModalHandler = {
    showDeleteModal: function (campaignActivityId, campaignActivityName) {
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

    showEditModal: async function (campaignActivityId, campaignActivityName) {
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
                <div class="modal-content large-modal" style="max-width: 900px; width: 95%;">
                    <div class="modal-header">
                        <h3 class="modal-title">캠페인 활동 수정 (<b>${campaignActivityName}</b>)</h3>
                        <button class="modal-close-button">&times;</button>
                    </div>
                    <div class="modal-body" style="max-height: 80vh; overflow-y: auto;">
                        <form id="edit-activity-form" class="space-y-6">
                            <!-- 1. Basic Info -->
                            <div class="section-card">
                                <h2 class="section-title">
                                    <span class="bg-black text-white w-6 h-6 rounded-full flex items-center justify-center text-xs mr-2">1</span>
                                    기본 정보
                                </h2>
                                <div class="space-y-5">
                                    <div>
                                        <label for="edit-campaignId" class="form-label">캠페인 선택 <span class="text-red-500">*</span></label>
                                        <select id="edit-campaignId" class="form-select">
                                            <option value="">캠페인을 선택하세요</option>
                                            ${campaigns.map(campaign => `<option value="${campaign.id}" ${campaign.id === activityDetails.campaignId ? 'selected' : ''}>${campaign.name}</option>`).join('')}
                                        </select>
                                    </div>

                                    <div>
                                        <label for="edit-activityName" class="form-label">활동 이름 <span class="text-red-500">*</span></label>
                                        <input type="text" id="edit-activityName" class="form-input" placeholder="예: 선착순 100명 커피 쿠폰 증정" value="${activityDetails.name}">
                                    </div>

                                    <!-- Image Upload -->
                                    <div>
                                        <label class="form-label">대표 이미지</label>
                                        <div class="flex flex-col gap-4">
                                            <div class="w-full aspect-video bg-gray-100 rounded-lg flex items-center justify-center overflow-hidden border border-gray-200 relative group max-w-md">
                                                <img id="edit-imagePreview" src="${activityDetails.imageUrl || ''}" alt="Preview" class="w-full h-full object-cover ${activityDetails.imageUrl ? '' : 'hidden'}">
                                                <div id="edit-imagePlaceholder" class="flex items-center justify-center w-full h-full ${activityDetails.imageUrl ? 'hidden' : ''}">
                                                    <i class="fa-regular fa-image text-gray-400 text-4xl"></i>
                                                </div>
                                                <div id="edit-imageOverlay" class="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer">
                                                    <i class="fa-solid fa-pen text-white text-2xl"></i>
                                                </div>
                                            </div>
                                            <div>
                                                <input type="file" id="edit-activityImage" class="hidden" accept="image/*">
                                                <button type="button" id="edit-uploadBtn" class="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50">
                                                    이미지 업로드
                                                </button>
                                                <p class="mt-1 text-xs text-gray-500">권장 사이즈: 1200x630px (JPG, PNG)</p>
                                            </div>
                                        </div>
                                    </div>

                                    <div>
                                        <label class="form-label mb-3">활동 유형 <span class="text-red-500">*</span></label>
                                        <input type="hidden" id="edit-activityTypeInput" name="campaignType" value="${activityDetails.activityType}" />
                                        <div class="grid grid-cols-3 gap-4">
                                            <div class="campaign-type-card selectable ${activityDetails.activityType === 'FIRST_COME_FIRST_SERVE' ? 'selected' : ''}" data-type="FIRST_COME_FIRST_SERVE">
                                                <i class="fa-solid fa-stopwatch type-icon"></i>
                                                <div class="font-medium">선착순</div>
                                                <div class="type-desc">빠른 참여 순서</div>
                                            </div>
                                            <div class="campaign-type-card selectable ${activityDetails.activityType === 'COUPON' ? 'selected' : ''}" data-type="COUPON">
                                                <i class="fa-solid fa-ticket type-icon"></i>
                                                <div class="font-medium">쿠폰</div>
                                                <div class="type-desc">미션 달성 시</div>
                                            </div>
                                            <div class="campaign-type-card selectable ${activityDetails.activityType === 'GIVEAWAY' ? 'selected' : ''}" data-type="GIVEAWAY">
                                                <i class="fa-solid fa-gift type-icon"></i>
                                                <div class="font-medium">응모/추첨</div>
                                                <div class="type-desc">랜덤 당첨</div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- 2. Schedule & Limits -->
                            <div class="section-card">
                                <h2 class="section-title">
                                    <span class="bg-black text-white w-6 h-6 rounded-full flex items-center justify-center text-xs mr-2">2</span>
                                    일정 및 제한
                                </h2>
                                <div class="space-y-5">
                                    <div class="grid grid-cols-1 md:grid-cols-2 gap-5">
                                        <div>
                                            <label for="edit-startDate" class="form-label">시작 일시 <span class="text-red-500">*</span></label>
                                            <input type="datetime-local" id="edit-startDate" class="form-input" value="${activityDetails.startDate ? activityDetails.startDate.substring(0, 16) : ''}">
                                        </div>
                                        <div>
                                            <label for="edit-endDate" class="form-label">종료 일시 <span class="text-red-500">*</span></label>
                                            <input type="datetime-local" id="edit-endDate" class="form-input" value="${activityDetails.endDate ? activityDetails.endDate.substring(0, 16) : ''}">
                                        </div>
                                    </div>

                                    <div>
                                        <label for="edit-limitCountInput" class="form-label">최대 참여 인원 <span class="text-red-500">*</span></label>
                                        <div class="relative">
                                            <input type="number" id="edit-limitCountInput" class="form-input pl-3 pr-12" placeholder="0" value="${activityDetails.limitCount || ''}">
                                            <div class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none">
                                                <span class="text-gray-500 sm:text-sm">명</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- 3. Targeting Filters -->
                            <div class="section-card">
                                <h2 class="section-title">
                                    <span class="bg-black text-white w-6 h-6 rounded-full flex items-center justify-center text-xs mr-2">3</span>
                                    타겟팅 필터
                                </h2>
                                <div class="space-y-4" id="edit-filter-container">
                                    <!-- Age Filter -->
                                    <div class="border border-gray-200 rounded-xl p-4 hover:border-gray-300 transition-colors">
                                        <label class="flex items-center cursor-pointer">
                                            <input type="checkbox" class="w-5 h-5 text-black border-gray-300 rounded focus:ring-black filter-type-checkbox" data-filter-type="AGE">
                                            <span class="ml-3 font-medium text-gray-900">나이 제한</span>
                                            <span class="ml-auto text-xs text-gray-500">특정 연령대만 참여 가능</span>
                                        </label>
                                        <div class="filter-details hidden mt-4 pl-8 border-t border-gray-100 pt-4" data-filter-details="AGE">
                                            <div id="age-filter-rows" class="space-y-3"></div>
                                            <button type="button" id="add-age-condition-btn" class="mt-3 text-sm text-blue-600 hover:text-blue-800 font-medium flex items-center">
                                                <i class="fa-solid fa-plus-circle mr-1"></i> 조건 추가
                                            </button>
                                        </div>
                                    </div>

                                    <!-- Region Filter -->
                                    <div class="border border-gray-200 rounded-xl p-4 hover:border-gray-300 transition-colors">
                                        <label class="flex items-center cursor-pointer">
                                            <input type="checkbox" class="w-5 h-5 text-black border-gray-300 rounded focus:ring-black filter-type-checkbox" data-filter-type="REGION">
                                            <span class="ml-3 font-medium text-gray-900">지역 제한</span>
                                            <span class="ml-auto text-xs text-gray-500">특정 거주 지역만 참여 가능</span>
                                        </label>
                                        <div class="filter-details hidden mt-4 pl-8 border-t border-gray-100 pt-4" data-filter-details="REGION">
                                            <div class="flex gap-2 mb-3">
                                                <select id="region-sido-select" class="form-select">
                                                    <option value="">시/도 선택</option>
                                                </select>
                                                <select id="region-sigungu-select" class="form-select">
                                                    <option value="">시/군/구 선택</option>
                                                </select>
                                            </div>
                                            <div id="selected-regions-container" class="flex flex-wrap gap-2"></div>
                                        </div>
                                    </div>

                                    <!-- VIP Tier Filter -->
                                    <div class="border border-gray-200 rounded-xl p-4 hover:border-gray-300 transition-colors">
                                        <label class="flex items-center cursor-pointer">
                                            <input type="checkbox" class="w-5 h-5 text-black border-gray-300 rounded focus:ring-black filter-type-checkbox" data-filter-type="GRADE">
                                            <span class="ml-3 font-medium text-gray-900">등급 제한</span>
                                            <span class="ml-auto text-xs text-gray-500">특정 회원 등급만 참여 가능</span>
                                        </label>
                                        <div class="filter-details hidden mt-4 pl-8 border-t border-gray-100 pt-4" data-filter-details="GRADE">
                                            <div class="flex gap-4 mb-4">
                                                <label class="flex items-center cursor-pointer">
                                                    <input type="radio" name="edit-vip-operator" value="IN" class="text-black focus:ring-black" checked>
                                                    <span class="ml-2 text-sm text-gray-700">포함 (선택한 등급만)</span>
                                                </label>
                                                <label class="flex items-center cursor-pointer">
                                                    <input type="radio" name="edit-vip-operator" value="NOT_IN" class="text-black focus:ring-black">
                                                    <span class="ml-2 text-sm text-gray-700">미포함 (선택한 등급 제외)</span>
                                                </label>
                                            </div>
                                            <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
                                                ${['BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND', 'VIP', 'VVIP'].map(tier => `
                                                    <label class="flex items-center p-2 border border-gray-100 rounded hover:bg-gray-50 cursor-pointer">
                                                        <input type="checkbox" class="text-black border-gray-300 rounded focus:ring-black filter-value-checkbox" data-type="GRADE" value="${tier}">
                                                        <span class="ml-2 text-sm">${tier}</span>
                                                    </label>
                                                `).join('')}
                                            </div>
                                        </div>
                                    </div>

                                    <!-- Dynamic Filters Container -->
                                    <div id="dynamic-filter-container" class="space-y-4"></div>

                                    <!-- Add Filter Button -->
                                    <div class="relative">
                                        <button type="button" id="edit-add-filter-btn" class="flex items-center gap-2 px-4 py-2 bg-gray-100 hover:bg-gray-200 rounded-lg text-sm font-medium transition-colors">
                                            <i class="fa-solid fa-plus"></i> 필터 추가
                                        </button>
                                        <!-- Filter Menu (Hidden by default) -->
                                        <div id="edit-add-filter-menu" class="hidden absolute top-full left-0 mt-2 w-48 bg-white rounded-lg shadow-lg border border-gray-100 z-10 py-1">
                                            <button type="button" class="w-full text-left px-4 py-2 text-sm hover:bg-gray-50 flex items-center gap-2" data-add-filter="RECENT_PURCHASE">
                                                <i class="fa-solid fa-cart-shopping text-gray-400"></i> 최근 구매 이력
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            <!-- 4. Product Info -->
                            <div class="section-card">
                                <h2 class="section-title">
                                    <span class="bg-black text-white w-6 h-6 rounded-full flex items-center justify-center text-xs mr-2">4</span>
                                    상품 정보
                                </h2>
                                <div class="space-y-5">
                                    <div>
                                        <label class="form-label">연동 상품</label>
                                        <div class="flex gap-2 mb-2">
                                            <input type="text" id="edit-selectedProductName" class="form-input bg-gray-50 cursor-not-allowed flex-1" placeholder="상품을 선택해주세요" readonly value="${activityDetails.productName || ''}">
                                            <input type="hidden" id="edit-selectedProductId" value="${activityDetails.productId || ''}">
                                            <button type="button" id="edit-openProductSearchBtn" class="open-product-search-btn px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shrink-0">
                                                상품 검색
                                            </button>
                                        </div>
                                        <div id="edit-productInfo" class="${activityDetails.productId ? '' : 'hidden'} p-3 bg-gray-50 rounded-lg border border-gray-200 text-sm">
                                            <div class="flex justify-between mb-1">
                                                <span class="text-gray-500">정상가</span>
                                                <span class="font-medium" id="edit-productOriginalPrice">${activityDetails.originalPrice || '-'}</span>
                                            </div>
                                            <div class="flex justify-between">
                                                <span class="text-gray-500">현재 재고</span>
                                                <span class="font-medium" id="edit-productStock">-</span>
                                            </div>
                                        </div>
                                    </div>

                                    <div class="grid grid-cols-2 gap-4">
                                        <div>
                                            <label for="edit-salePrice" class="form-label">판매 가격</label>
                                            <div class="relative">
                                                <input type="number" id="edit-salePrice" class="form-input pr-12" placeholder="예: 10000" value="${activityDetails.price || ''}">
                                                <div class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none">
                                                    <span class="text-gray-500 sm:text-sm">원</span>
                                                </div>
                                            </div>
                                        </div>
                                        <div>
                                            <label for="edit-saleQuantity" class="form-label">판매 수량</label>
                                            <div class="relative">
                                                <input type="number" id="edit-saleQuantity" class="form-input pr-12" placeholder="예: 100" value="${activityDetails.quantity || ''}">
                                                <div class="absolute inset-y-0 right-0 pr-3 flex items-center pointer-events-none">
                                                    <span class="text-gray-500 sm:text-sm">개</span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button id="save-edit-btn" class="btn-primary">저장</button>
                        <button id="cancel-edit-btn" class="btn-secondary">취소</button>
                    </div>
                </div>

                <!-- Product Search Modal (Nested) -->
                <div id="edit-productSearchModal" class="fixed inset-0 bg-black bg-opacity-50 hidden items-center justify-center z-[2050]">
                    <div class="bg-white rounded-xl shadow-2xl w-full max-w-lg mx-4 overflow-hidden">
                        <div class="p-4 border-b border-gray-100 flex justify-between items-center">
                            <h3 class="font-bold text-lg">상품 검색</h3>
                            <button type="button" id="edit-closeProductSearchBtn" class="close-product-search-btn text-gray-400 hover:text-gray-600">
                                <i class="fa-solid fa-xmark text-xl"></i>
                            </button>
                        </div>
                        <div class="p-4">
                            <div class="flex gap-2 mb-4">
                                <input type="text" id="edit-productSearchInput" class="form-input" placeholder="상품명을 입력하세요">
                                <button type="button" id="edit-productSearchActionBtn" class="px-4 py-2 bg-gray-800 text-white rounded-lg hover:bg-gray-900">
                                    검색
                                </button>
                            </div>
                            <div class="h-64 overflow-y-auto border border-gray-100 rounded-lg">
                                <ul id="edit-productSearchResults" class="divide-y divide-gray-100">
                                    <li class="p-4 text-center text-gray-500 text-sm">검색 결과가 없습니다.</li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        document.body.insertAdjacentHTML('beforeend', modalHtml);

        const modalBackdrop = document.getElementById('edit-modal-backdrop');
        const closeModal = () => modalBackdrop.remove();
        modalBackdrop.querySelector('.modal-close-button').addEventListener('click', closeModal);
        document.getElementById('cancel-edit-btn').addEventListener('click', closeModal);

        // --- Shared Module Initialization ---
        const { FilterRegistry, Helpers } = CampaignActivityModule;

        // 1. Image Upload
        let uploadedImageUrl = activityDetails.imageUrl || "";
        Helpers.initImageUpload(modalBackdrop, 'edit-activityImage', 'edit-imagePreview', 'edit-imagePlaceholder', 'edit-imageOverlay', 'edit-uploadBtn', (url) => {
            uploadedImageUrl = url;
        });

        // 2. Product Search
        Helpers.initProductSearch(modalBackdrop, 'edit-productSearchModal', 'edit-productSearchInput', 'edit-productSearchResults', 'edit-productSearchActionBtn', (product) => {
            document.getElementById('edit-selectedProductId').value = product.id;
            document.getElementById('edit-selectedProductName').value = product.name;
            document.getElementById('edit-productOriginalPrice').textContent = `${product.price}원`;
            document.getElementById('edit-productStock').textContent = `${product.stock}개`;
            document.getElementById('edit-productInfo').classList.remove('hidden');
        });

        // 3. Filters
        FilterRegistry.initStaticFilters(modalBackdrop);

        // Add Filter Button Logic
        const addFilterBtn = modalBackdrop.querySelector('#edit-add-filter-btn');
        const addFilterMenu = modalBackdrop.querySelector('#edit-add-filter-menu');

        if (addFilterBtn && addFilterMenu) {
            addFilterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                addFilterMenu.classList.toggle('hidden');
            });

            document.addEventListener('click', () => addFilterMenu.classList.add('hidden'));

            addFilterMenu.querySelectorAll('[data-add-filter]').forEach(btn => {
                btn.addEventListener('click', () => {
                    const type = btn.dataset.addFilter;
                    FilterRegistry.addDynamicFilter(modalBackdrop, type);
                });
            });
        }

        // 4. Populate Existing Filters
        const existingFilters = activityDetails.filters || [];
        FilterRegistry.populateAll(modalBackdrop, existingFilters);

        // 5. Type Selection
        const activityTypeInput = document.getElementById('edit-activityTypeInput');
        modalBackdrop.querySelectorAll('.campaign-type-card.selectable').forEach(button => {
            button.addEventListener('click', () => {
                modalBackdrop.querySelectorAll('.campaign-type-card.selectable').forEach(btn => btn.classList.remove('selected'));
                button.classList.add('selected');
                activityTypeInput.value = button.dataset.type;
            });
        });

        // 6. Filters Common Logic (Toggle Details)
        const filterTypeCheckboxes = modalBackdrop.querySelectorAll('.filter-type-checkbox');
        const toggleDetails = (checkbox) => {
            const details = modalBackdrop.querySelector(`.filter-details[data-filter-details="${checkbox.dataset.filterType}"]`);
            if (details) details.classList.toggle('hidden', !checkbox.checked);
        };
        filterTypeCheckboxes.forEach(cb => cb.addEventListener('change', () => toggleDetails(cb)));


        // --- Submit Handler ---
        document.getElementById('save-edit-btn').addEventListener('click', async () => {
            const name = document.getElementById('edit-activityName').value;
            const campaignId = document.getElementById('edit-campaignId').value;
            const type = document.getElementById('edit-activityTypeInput').value;
            const startDate = document.getElementById('edit-startDate').value;
            const endDate = document.getElementById('edit-endDate').value;
            const limitCount = document.getElementById('edit-limitCountInput').value;
            const productId = document.getElementById('edit-selectedProductId').value;
            const salePrice = document.getElementById('edit-salePrice').value;
            const saleQuantity = document.getElementById('edit-saleQuantity').value;

            if (!name || !campaignId || !type || !startDate || !endDate || !limitCount) return alert('필수 항목 입력');

            if (new Date(startDate) > new Date(endDate)) {
                alert("종료 일시는 시작 일시보다 뒤여야 합니다.");
                return;
            }

            // Product Validation
            if (productId) {
                if (!salePrice || parseInt(salePrice) < 0) {
                    alert("상품 판매 가격은 0원 이상이어야 합니다.");
                    return;
                }
                if (!saleQuantity || parseInt(saleQuantity) <= 0) {
                    alert("상품 판매 수량은 1개 이상이어야 합니다.");
                    return;
                }
            }

            const filters = FilterRegistry.extractAll(modalBackdrop);
            if (filters === null) return; // Validation error occurred

            const payload = {
                name, campaignId: parseInt(campaignId), activityType: type, startDate: startDate + ':00', endDate: endDate + ':00',
                limitCount: parseInt(limitCount), filters, imageUrl: uploadedImageUrl, status: activityDetails.status,
                productId: productId ? parseInt(productId) : null,
                price: salePrice ? parseInt(salePrice) : 0,
                quantity: saleQuantity ? parseInt(saleQuantity) : 0
            };

            try {
                const res = await fetch(`/api/v1/campaign/activities/${campaignActivityId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${common.getCookie("accessToken")}` },
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    alert('수정 완료');
                    closeModal();
                    location.reload();
                } else {
                    const err = await res.json();
                    alert('수정 실패: ' + (err.message || '알 수 없는 오류'));
                }
            } catch (e) {
                console.error(e);
                alert('오류 발생');
            }
        });
    },

    showStatusModal: function (campaignActivityId, campaignActivityName, currentStatus, newStatus) {
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
        if (currentStatus === "ACTIVE" && newStatus === "ENDED") {
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

    showCreateCampaignModal: function () {
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
                        <form id="create-campaign-form" class="space-y-4">
                            <div>
                                <label for="new-campaign-name" class="form-label">캠페인 이름 *</label>
                                <div class="flex items-center gap-2">
                                    <input type="text" id="new-campaign-name" class="form-input" required>
                                    <button type="button" id="check-duplicate-btn" class="btn-secondary whitespace-nowrap">중복 확인</button>
                                </div>
                                <p id="duplicate-check-result" class="text-sm mt-1 h-5"></p>
                            </div>
                            <div class="grid grid-cols-2 gap-4">
                                <div>
                                    <label for="new-campaign-start" class="form-label">시작일 *</label>
                                    <input type="datetime-local" id="new-campaign-start" class="form-input" required>
                                </div>
                                <div>
                                    <label for="new-campaign-end" class="form-label">종료일 *</label>
                                    <input type="datetime-local" id="new-campaign-end" class="form-input" required>
                                </div>
                            </div>
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
        const startDateInput = document.getElementById('new-campaign-start');
        const endDateInput = document.getElementById('new-campaign-end');

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
            const startAt = startDateInput.value;
            const endAt = endDateInput.value;

            if (!startAt || !endAt) {
                alert('시작일과 종료일을 모두 입력해주세요.');
                return;
            }

            const startDate = new Date(startAt);
            const endDate = new Date(endAt);
            const now = new Date();
            // Reset seconds/milliseconds for fair comparison
            now.setSeconds(0, 0);

            if (startDate < now) {
                alert('시작일은 현재 시간 이후여야 합니다.');
                return;
            }

            if (startDate >= endDate) {
                alert('시작일은 종료일보다 이전이어야 합니다.');
                return;
            }

            const payload = {
                name: name,
                startAt: startAt + ':00', // Append seconds if needed by backend format
                endAt: endAt + ':00'
            };
            const token = common.getCookie("accessToken");

            try {
                const res = await fetch('/api/v1/campaign', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${token}`
                    },
                    body: JSON.stringify(payload)
                });

                if (res.ok) {
                    alert('캠페인이 성공적으로 생성되었습니다!');
                    closeModal();
                    // Optionally refresh the campaign list if needed, or just let the user continue
                } else {
                    const errorText = await res.text();
                    alert('캠페인 생성 실패: ' + errorText);
                }
            } catch (error) {
                console.error('캠페인 생성 오류:', error);
                alert('캠페인 생성 중 오류가 발생했습니다.');
            }
        });
    }
};
