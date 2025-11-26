window.CampaignActivityModule = {
    // ==========================================
    // Filter Registry & Handlers
    // ==========================================
    FilterRegistry: {
        handlers: {},
        register(type, handler) { this.handlers[type] = handler; },
        get(type) { return this.handlers[type]; },

        // Initialize static filters (Age, Region, Grade)
        initStaticFilters(container) {
            ['AGE', 'REGION', 'GRADE'].forEach(type => {
                const handler = this.handlers[type];
                if (handler && handler.init) handler.init(container);
            });
        },

        populateAll(container, filters) {
            if (!filters || !Array.isArray(filters)) return;

            filters.forEach(filter => {
                const handler = this.handlers[filter.type];
                if (handler) {
                    // For dynamic filters, we might need to add them first
                    if (filter.type === 'RECENT_PURCHASE') {
                        this.addDynamicFilter(container, filter.type, filter);
                    } else if (handler.populate) {
                        handler.populate(container, filter);
                    }
                }
            });
        },

        extractAll(container, options = { silent: false }) {
            const filters = [];
            let hasError = false;

            // Static Filters
            container.querySelectorAll('.filter-type-checkbox:checked').forEach(cb => {
                if (hasError && !options.silent) return;
                const type = cb.dataset.filterType;
                const handler = this.handlers[type];
                if (handler && handler.extract) {
                    try {
                        const extracted = handler.extract(container);
                        if (extracted) {
                            if (Array.isArray(extracted)) filters.push(...extracted);
                            else filters.push(extracted);
                        }
                    } catch (e) {
                        if (!options.silent) {
                            hasError = true;
                            if (e.message) alert(e.message);
                        }
                        // If silent, we just ignore this filter (it's incomplete or invalid, so don't show in preview)
                    }
                }
            });

            if (hasError) return null;

            // Dynamic Filters (Recent Purchase)
            const purchaseHandler = this.handlers['RECENT_PURCHASE'];
            if (purchaseHandler) {
                try {
                    const extracted = purchaseHandler.extract(container);
                    if (extracted) filters.push(extracted);
                } catch (e) {
                    if (!options.silent) {
                        if (e.message) alert(e.message);
                        return null;
                    }
                    // If silent, ignore
                }
            }

            return filters;
        },

        addDynamicFilter(container, type, data = null) {
            const handler = this.handlers[type];
            if (!handler || !handler.render) return;

            const dynamicContainer = container.querySelector('#dynamic-filter-container');
            if (!dynamicContainer) return;

            // Check if already exists (for singleton filters like Purchase)
            if (dynamicContainer.querySelector(`[data-filter-type="${type}"]`)) {
                alert('이미 추가된 필터입니다.');
                return;
            }

            const html = handler.render(type);
            dynamicContainer.insertAdjacentHTML('beforeend', html);

            const newFilterEl = dynamicContainer.lastElementChild;
            if (handler.init) handler.init(newFilterEl);

            // If data provided (e.g. edit mode), populate it
            if (data && handler.populate) {
                handler.populate(newFilterEl, data);
            }

            // Add remove listener
            const removeBtn = newFilterEl.querySelector('.remove-filter-btn');
            if (removeBtn) {
                removeBtn.addEventListener('click', () => newFilterEl.remove());
            }
        }
    },

    // ==========================================
    // Helper Functions
    // ==========================================
    Helpers: {
        initImageUpload(container, inputId, previewId, placeholderId, overlayId, uploadBtnId, callback) {
            const input = container.querySelector(`#${inputId}`);
            const preview = container.querySelector(`#${previewId}`);
            const placeholder = container.querySelector(`#${placeholderId}`);
            const overlay = container.querySelector(`#${overlayId}`);
            const btn = container.querySelector(`#${uploadBtnId}`);

            if (!input) return;

            const trigger = () => input.click();
            if (btn) btn.addEventListener('click', trigger);
            if (overlay) overlay.addEventListener('click', trigger);

            input.addEventListener('change', async (e) => {
                const file = e.target.files[0];
                if (!file) return;
                const formData = new FormData();
                formData.append('file', file);
                try {
                    const res = await fetch('/api/v1/files/upload', { method: 'POST', body: formData });
                    if (res.ok) {
                        const data = await res.json();
                        if (preview) {
                            preview.src = data.url;
                            preview.classList.remove('hidden');
                        }
                        if (placeholder) placeholder.classList.add('hidden');
                        if (callback) callback(data.url);
                    } else {
                        alert('이미지 업로드 실패');
                    }
                } catch (e) { console.error(e); alert('오류 발생'); }
            });
        },

        initProductSearch(container, modalId, searchInputId, resultListId, searchBtnId, onSelect) {
            const modal = document.getElementById(modalId); // Modal is usually global
            const openBtn = container.querySelector('.open-product-search-btn');
            const closeBtn = modal ? modal.querySelector('.close-product-search-btn') : null;
            const searchBtn = modal ? modal.querySelector(`#${searchBtnId}`) : null;
            const input = modal ? modal.querySelector(`#${searchInputId}`) : null;
            const list = modal ? modal.querySelector(`#${resultListId}`) : null;

            if (openBtn && modal) {
                openBtn.addEventListener('click', () => {
                    modal.classList.remove('hidden');
                    modal.classList.add('flex');
                });
            }

            if (closeBtn && modal) {
                closeBtn.addEventListener('click', () => {
                    modal.classList.add('hidden');
                    modal.classList.remove('flex');
                });
            }

            if (searchBtn && input && list) {
                searchBtn.addEventListener('click', async () => {
                    const kw = input.value;
                    if (!kw) return alert('검색어 입력');
                    try {
                        const res = await fetch(`/api/v1/products/search?keyword=${encodeURIComponent(kw)}`);
                        const products = await res.json();
                        list.innerHTML = '';
                        if (!products.length) list.innerHTML = '<li class="p-4 text-center text-gray-500 text-sm">검색 결과 없음</li>';
                        products.forEach(p => {
                            const li = document.createElement('li');
                            li.className = 'p-4 hover:bg-gray-50 cursor-pointer flex justify-between items-center';
                            li.innerHTML = `<div><div class="font-medium">${p.name}</div><div class="text-xs text-gray-500">재고: ${p.stock} | ${p.price}원</div></div><button class="px-3 py-1 text-xs bg-blue-100 text-blue-600 rounded">선택</button>`;
                            const select = () => {
                                onSelect(p);
                                modal.classList.add('hidden');
                                modal.classList.remove('flex');
                            };
                            li.addEventListener('click', select);
                            li.querySelector('button').addEventListener('click', (e) => { e.stopPropagation(); select(); });
                            list.appendChild(li);
                        });
                    } catch (e) { alert('검색 실패'); }
                });
            }
        }
    }
};

// ==========================================
// Filter Implementations
// ==========================================

// AGE
CampaignActivityModule.FilterRegistry.register('AGE', {
    init(container) {
        const rowsContainer = container.querySelector('#age-filter-rows');
        const addBtn = container.querySelector('#add-age-condition-btn');
        if (!rowsContainer || !addBtn) return;

        this.createRow = (op = 'LTE', v1 = '', v2 = '') => {
            const div = document.createElement('div');
            div.className = "flex items-center gap-2 age-filter-row bg-gray-50 p-2 rounded-lg";
            div.innerHTML = `
                <select class="form-select age-operator">
                    <option value="LTE" ${op === 'LTE' ? 'selected' : ''}>이하 (LTE)</option>
                    <option value="GTE" ${op === 'GTE' ? 'selected' : ''}>이상 (GTE)</option>
                    <option value="BETWEEN" ${op === 'BETWEEN' ? 'selected' : ''}>범위 (BETWEEN)</option>
                </select>
                <div class="age-inputs flex items-center gap-2">
                    <input type="number" placeholder="나이" class="form-input w-20 age-val-1 text-sm" value="${v1}">
                    ${op === 'BETWEEN' ? `<span class="text-gray-500">~</span><input type="number" placeholder="나이" class="form-input w-20 age-val-2 text-sm" value="${v2}">` : ''}
                </div>
                <button type="button" class="remove-age-row-btn text-red-500 hover:text-red-700 ml-auto"><i class="fa-solid fa-trash"></i></button>
            `;
            div.querySelector('.remove-age-row-btn').addEventListener('click', () => div.remove());
            div.querySelector('.age-operator').addEventListener('change', (e) => {
                const inputs = div.querySelector('.age-inputs');
                const val1 = inputs.querySelector('.age-val-1').value;
                if (e.target.value === 'BETWEEN') {
                    inputs.innerHTML = `<input type="number" placeholder="나이" class="form-input w-20 age-val-1 text-sm" value="${val1}"><span class="text-gray-500">~</span><input type="number" placeholder="나이" class="form-input w-20 age-val-2 text-sm">`;
                } else {
                    inputs.innerHTML = `<input type="number" placeholder="나이" class="form-input w-20 age-val-1 text-sm" value="${val1}">`;
                }
            });
            return div;
        };

        addBtn.addEventListener('click', () => {
            if (rowsContainer.children.length >= 5) return alert('최대 5개');
            rowsContainer.appendChild(this.createRow());
        });
    },
    populate(container, filter) {
        const cb = container.querySelector(`.filter-type-checkbox[data-filter-type="AGE"]`);
        if (cb) {
            cb.checked = true;
            cb.dispatchEvent(new Event('change'));
            const rowsContainer = container.querySelector('#age-filter-rows');
            const v1 = filter.values[0];
            const v2 = filter.values.length > 1 ? filter.values[1] : '';
            rowsContainer.appendChild(this.createRow(filter.operator, v1, v2));
        }
    },
    extract(container) {
        const filters = [];
        let error = null;
        container.querySelectorAll('.age-filter-row').forEach(row => {
            if (error) return;
            const op = row.querySelector('.age-operator').value;
            const v1 = row.querySelector('.age-val-1').value;
            const v2 = row.querySelector('.age-val-2')?.value;

            if (!v1) {
                error = new Error('나이를 입력해주세요.');
                return;
            }
            if (parseInt(v1) < 0) {
                error = new Error('나이는 0보다 작을 수 없습니다.');
                return;
            }

            if (op === 'BETWEEN') {
                if (!v2) {
                    error = new Error('나이 범위를 모두 입력해주세요.');
                    return;
                }
                if (parseInt(v2) < 0) {
                    error = new Error('나이는 0보다 작을 수 없습니다.');
                    return;
                }
                if (parseInt(v1) > parseInt(v2)) {
                    error = new Error('시작 나이가 종료 나이보다 클 수 없습니다.');
                    return;
                }
                filters.push({ type: 'AGE', operator: op, values: [v1, v2], phase: 'FAST' });
            } else {
                filters.push({ type: 'AGE', operator: op, values: [v1], phase: 'FAST' });
            }
        });

        if (error) throw error;
        return filters;
    }
});

// REGION
CampaignActivityModule.FilterRegistry.register('REGION', {
    init(container) {
        const sidoSelect = container.querySelector('#region-sido-select');
        const sigunguSelect = container.querySelector('#region-sigungu-select');
        const regionsContainer = container.querySelector('#selected-regions-container');
        if (!sidoSelect || !sigunguSelect) return;

        let districtData = {};
        fetch("/data/korean-districts.json").then(r => r.json()).then(data => {
            districtData = data;
            Object.keys(data).forEach(sido => sidoSelect.add(new Option(sido, sido)));
        }).catch(console.error);

        sidoSelect.addEventListener('change', () => {
            sigunguSelect.innerHTML = '<option value="">시/군/구 선택</option><option value="ALL">전체</option>';
            if (districtData[sidoSelect.value]) {
                districtData[sidoSelect.value].forEach(s => sigunguSelect.add(new Option(s, s)));
            }
        });

        this.addRegionTag = (value) => {
            if (Array.from(regionsContainer.querySelectorAll('.selected-region-tag')).some(t => t.dataset.region === value)) return alert('이미 추가됨');
            const tag = document.createElement('span');
            tag.className = "selected-region-tag bg-gray-200 text-gray-800 text-sm font-medium mr-2 px-2.5 py-0.5 rounded flex items-center gap-1";
            tag.dataset.region = value;
            tag.innerHTML = `${value.replace('-', ' ')} <button type="button" class="text-red-500 hover:text-red-700 ml-1">&times;</button>`;
            tag.querySelector('button').addEventListener('click', () => tag.remove());
            regionsContainer.appendChild(tag);
        };

        sigunguSelect.addEventListener('change', () => {
            if (!sidoSelect.value || !sigunguSelect.value) return;
            this.addRegionTag(sigunguSelect.value === 'ALL' ? sidoSelect.value : `${sidoSelect.value}-${sigunguSelect.value}`);
        });
    },
    populate(container, filter) {
        const cb = container.querySelector(`.filter-type-checkbox[data-filter-type="REGION"]`);
        if (cb) {
            cb.checked = true;
            cb.dispatchEvent(new Event('change'));
            filter.values.forEach(v => this.addRegionTag(v));
        }
    },
    extract(container) {
        const regions = Array.from(container.querySelectorAll('.selected-region-tag')).map(t => t.dataset.region);
        if (regions.length) return { type: 'REGION', operator: 'IN', values: regions, phase: 'FAST' };
        return null;
    }
});

// GRADE
CampaignActivityModule.FilterRegistry.register('GRADE', {
    init(container) { },
    populate(container, filter) {
        const cb = container.querySelector(`.filter-type-checkbox[data-filter-type="GRADE"]`);
        if (cb) {
            cb.checked = true;
            cb.dispatchEvent(new Event('change'));
            const opRadio = container.querySelector(`input[name="vip-operator"][value="${filter.operator}"]`) || container.querySelector(`input[name="edit-vip-operator"][value="${filter.operator}"]`);
            if (opRadio) opRadio.checked = true;
            filter.values.forEach(v => {
                const vcb = container.querySelector(`.filter-value-checkbox[value="${v}"]`);
                if (vcb) vcb.checked = true;
            });
        }
    },
    extract(container) {
        const tiers = Array.from(container.querySelectorAll('.filter-value-checkbox[data-type="GRADE"]:checked')).map(c => c.value);
        const opInput = container.querySelector('input[name="vip-operator"]:checked') || container.querySelector('input[name="edit-vip-operator"]:checked');
        const op = opInput ? opInput.value : 'IN';
        if (tiers.length) return { type: 'GRADE', operator: op, values: tiers, phase: 'FAST' };
        return null;
    }
});

// RECENT_PURCHASE (Dynamic)
CampaignActivityModule.FilterRegistry.register('RECENT_PURCHASE', {
    render(type) {
        return `
            <div class="border border-gray-200 rounded-xl p-4 hover:border-gray-300 transition-colors mb-3" data-filter-type="RECENT_PURCHASE">
                <div class="flex items-center justify-between mb-3">
                    <span class="font-medium text-gray-900">최근 구매 이력</span>
                    <button type="button" class="remove-filter-btn text-gray-400 hover:text-red-500">
                        <i class="fa-solid fa-xmark"></i>
                    </button>
                </div>
                <div class="filter-details">
                    <div class="flex gap-4 mb-3">
                        <select class="form-select purchase-operator w-auto">
                            <option value="BETWEEN">기간 (BETWEEN)</option>
                            <option value="NOT_BETWEEN">제외 기간 (NOT_BETWEEN)</option>
                            <option value="GTE">이후 (GTE)</option>
                            <option value="LTE">이전 (LTE)</option>
                        </select>
                    </div>
                    <div class="purchase-inputs flex items-center gap-2">
                        <input type="datetime-local" class="form-input w-40 purchase-val-1 text-sm">
                        <span class="text-gray-500 range-separator">~</span>
                        <input type="datetime-local" class="form-input w-40 purchase-val-2 text-sm">
                    </div>
                </div>
            </div>
        `;
    },
    init(el) {
        const operatorSelect = el.querySelector('.purchase-operator');
        const inputsContainer = el.querySelector('.purchase-inputs');

        const updateInputs = () => {
            const op = operatorSelect.value;
            if (op === 'BETWEEN' || op === 'NOT_BETWEEN') {
                inputsContainer.innerHTML = `
                    <input type="datetime-local" class="form-input w-40 purchase-val-1 text-sm">
                    <span class="text-gray-500 range-separator">~</span>
                    <input type="datetime-local" class="form-input w-40 purchase-val-2 text-sm">
                `;
            } else {
                inputsContainer.innerHTML = `
                    <input type="datetime-local" class="form-input w-40 purchase-val-1 text-sm">
                `;
            }
        };

        operatorSelect.addEventListener('change', updateInputs);
        // Initial state is BETWEEN, so no need to call updateInputs if HTML matches
    },
    populate(el, filter) {
        const operatorSelect = el.querySelector('.purchase-operator');
        operatorSelect.value = filter.operator;
        operatorSelect.dispatchEvent(new Event('change'));

        const v1 = filter.values[0];
        const v2 = filter.values.length > 1 ? filter.values[1] : '';

        el.querySelector('.purchase-val-1').value = v1 ? v1.substring(0, 16) : '';
        const val2Input = el.querySelector('.purchase-val-2');
        if (val2Input && v2) val2Input.value = v2.substring(0, 16);
    },
    extract(container) {
        const el = container.querySelector('[data-filter-type="RECENT_PURCHASE"]');
        if (!el) return null;

        const op = el.querySelector('.purchase-operator').value;
        const v1 = el.querySelector('.purchase-val-1').value;
        const v2 = el.querySelector('.purchase-val-2')?.value;

        if (!v1) throw new Error('최근 구매 이력의 날짜를 입력해주세요.');

        if ((op === 'BETWEEN' || op === 'NOT_BETWEEN')) {
            if (!v2) throw new Error('최근 구매 이력의 종료 날짜를 입력해주세요.');
            if (new Date(v1) > new Date(v2)) {
                throw new Error('최근 구매 이력의 시작일이 종료일보다 클 수 없습니다.');
            }
            return { type: 'RECENT_PURCHASE', operator: op, values: [v1 + ":00", v2 + ":00"], phase: 'HEAVY' };
        } else {
            return { type: 'RECENT_PURCHASE', operator: op, values: [v1 + ":00"], phase: 'HEAVY' };
        }
    }
});
