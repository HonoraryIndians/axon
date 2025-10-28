document.addEventListener("DOMContentLoaded", () => {
    const submit = document.getElementById("submit");
    // TO-DO: 관리자용 토큰 생성 후 적용

    const token = common.getCookie("accessToken");

    if (!token) {
        alert("관리자 토큰이 필요합니다. 다시 로그인하세요.");
        window.location.href = "/";
        return;
    }
    const campaignSelect = document.getElementById('campaignId');
    function loadCampaign() {
        fetch("/api/v1/campaign")
            .then(res => res.json())
            .then(campaigns => {
                campaigns.forEach(campaign => {
                    const opt = document.createElement('option');
                    opt.value=campaign.id;
                    opt.textContent = campaign.name;
                    campaignSelect.appendChild(opt);
                });
            })
            .catch(error => {
                const def = document.getElementById("cam_default");
                def.textContent = "캠페인을 선택하지 못합니다.";
                console.log(error);
            })
    }
    loadCampaign();

    const selectableTypes = document.querySelectorAll(".campaign-type.selectable");
    const eventTypeInput = document.getElementById("eventTypeInput");

    selectableTypes.forEach((btn) => {
        btn.addEventListener("click", () => {
            selectableTypes.forEach((b) => b.classList.remove("selected"));
            btn.classList.add("selected");
            eventTypeInput.value = btn.dataset.type;
        });
    });

        // 필터 UI 로직

        const filterTypeCheckboxes = document.querySelectorAll('.filter-type-checkbox');

        filterTypeCheckboxes.forEach(checkbox => {
            checkbox.addEventListener('change', () => {
                const filterType = checkbox.dataset.filterType;
                const detailsDiv = document.querySelector(`.filter-details[data-filter-details="${filterType}"]`);

                if (checkbox.checked) {
                    detailsDiv.classList.remove('hidden');
                } else {
                    detailsDiv.classList.add('hidden');
                    // 상위 체크박스 해제 시 하위 입력/체크박스 초기화
                    detailsDiv.querySelectorAll('input, select').forEach(input => {
                        if(input.type === 'checkbox' || input.type === 'radio') input.checked = false;
                        else input.value = '';
                    });
                }
            });
        });

        // 나이 범위 추가 로직

        const addAgeRangeBtn = document.getElementById('add-age-range-btn');
        const ageFilterInputs = document.getElementById('age-filter-inputs');
        addAgeRangeBtn.addEventListener('click', () => {
            if (ageFilterInputs.children.length < 10) {
                const newAgeRangeDiv = document.createElement('div');
                newAgeRangeDiv.className = 'flex items-center gap-2 mb-2';
                newAgeRangeDiv.innerHTML = `
                    <input type="number" placeholder="20" class="form-input age-range-start w-20"> ~ <input type="number" placeholder="29" class="form-input age-range-end w-20">
                    <button type="button" class="remove-age-range-btn text-red-500">-</button>
                `;
                ageFilterInputs.appendChild(newAgeRangeDiv);
            }
        });

        ageFilterInputs.addEventListener('click', (e) => {
            if (e.target.classList.contains('remove-age-range-btn')) {
                e.target.parentElement.remove();
            }
        });

        // 지역 선택 로직
        const sidoSelect = document.getElementById('region-sido-select');
        const sigunguSelect = document.getElementById('region-sigungu-select');
        const selectedRegionsContainer = document.getElementById('selected-regions-container');
        let districtData = {};

        fetch('/data/korean-districts.json')
            .then(response => response.json())
            .then(data => {
                districtData = data;
                for (const sido in districtData) {
                    const option = new Option(sido, sido);
                    sidoSelect.add(option);
                }
            });

            sidoSelect.addEventListener('change', () => {
                sigunguSelect.innerHTML = '<option value="">시/군/구 선택</option><option value="ALL">전체</option>';

                const selectedSido = sidoSelect.value;
                if (selectedSido && districtData[selectedSido]) {
                    districtData[selectedSido].forEach(sigungu => {
                        const option = new Option(sigungu, sigungu);
                        sigunguSelect.add(option);
                    });
                }
            });

            sigunguSelect.addEventListener('change', () => {
                const sido = sidoSelect.value;
                const sigungu = sigunguSelect.value;
                if (!sido || !sigungu) return;
                const regionValue = sigungu === 'ALL' ? sido : `${sido}-${sigungu}`;
                const regionText = sigungu === 'ALL' ? `${sido} 전체` : `${sido} ${sigungu}`;
                const existingTags = document.querySelectorAll('.selected-region-tag');
                for (let tag of existingTags) {
                    if (tag.dataset.region === regionValue) {
                        alert('이미 추가된 지역입니다.');
                        return;
                    }
                }
                const regionTag = document.createElement('span');
                regionTag.className = 'selected-region-tag bg-gray-200 text-gray-800 text-sm font-medium mr-2 px-2.5 py-0.5 rounded';
                regionTag.textContent = regionText;
                regionTag.dataset.region = regionValue;
                const removeBtn = document.createElement('button');
                removeBtn.className = 'ml-2 text-red-500';
                removeBtn.innerHTML = '&times;';
                removeBtn.onclick = () => regionTag.remove();
                regionTag.appendChild(removeBtn);
                selectedRegionsContainer.appendChild(regionTag);

            });

        submit.addEventListener("click", async () => {
            const campaignId = document.getElementById("campaignId").value;
            const eventType = eventTypeInput.value;
            const eventName = document.getElementById("eventName").value.trim();
            const limitCount = document.getElementById("limitCountInput").value;
            const startDate = document.getElementById("startDate").value;
            const endDate = document.getElementById("endDate").value;
            const status = "DRAFT"

            if (!campaignId || !eventName || !status || !startDate || !endDate || !eventType) {
                alert("필수 항목을 반드시 선택하세요");
                return;
            }
            if(limitCount <=0) {alert("최소 참여 인원은 1명입니다."); return;}

            const now = new Date();
            const start = new Date(startDate);
            const end = new Date(endDate);
            if (start <= now) { alert("시작 시간은 현재 시간보다 미래여야 합니다."); return; }
            if (end <= now) { alert("종료 시간은 현재 시간보다 미래여야 합니다."); return; }
            if (end <= start) { alert("종료 시간은 시작 시간보다 미래여야 합니다."); return; }

            // 필터 데이터 수집
            const filters = [];
            const activeFilterGroups = document.querySelectorAll('.filter-type-checkbox:checked');

                    activeFilterGroups.forEach(groupCheckbox => {
                        const type = groupCheckbox.dataset.filterType;
                        let values = [];
                        let isValid = true; // 유효성 검사 플래그
            
                        if (type === 'AGE') {
                            const ageRanges = document.querySelectorAll('#age-filter-inputs .flex');
                            ageRanges.forEach(range => {
                                const startAgeInput = range.querySelector('.age-range-start');
                                const endAgeInput = range.querySelector('.age-range-end');
                                const startAge = startAgeInput.value;
                                const endAge = endAgeInput.value;
            
                                if (!startAge || !endAge) {
                                    alert('나이 범위를 모두 입력해주세요.');
                                    isValid = false;
                                    return;
                                }
                                if (parseInt(startAge) < 0 || parseInt(endAge) < 0) {
                                    alert('나이는 0 이상이어야 합니다.');
                                    isValid = false;
                                    return;
                                }
                                if (parseInt(endAge) < parseInt(startAge)) {
                                    alert('끝 나이는 시작 나이보다 크거나 같아야 합니다.');
                                    isValid = false;
                                    return;
                                }
                                values.push(`${startAge}-${endAge}`);
                            });
                            if (!isValid) return; // 유효성 검사 실패 시 중단
                        } else if (type === 'REGION') {                    const regionTags = document.querySelectorAll('.selected-region-tag');
                    regionTags.forEach(tag => {
                        values.push(tag.dataset.region);
                    });
                } else {
                    const valueCheckboxes = document.querySelectorAll(`.filter-details[data-filter-details="${type}"] .filter-value-checkbox:checked`);
                    values = Array.from(valueCheckboxes).map(cb => cb.value);
                }

                if (values.length > 0) {
                    filters.push({ type: type, values: values });
                }
            });

            const payload = {
                name: eventName,
                limitCount: limitCount ? Number(limitCount) : 0,
                status,
                startDate,
                endDate,
                eventType,
                filters: filters
            };

        try {
            const res = await fetch(`/api/v1/event/${campaignId}/events`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}` // 관리자 토큰 교체
                },
                body: JSON.stringify(payload)
            });
            if(!res.ok) {
                alert("생성에 실패하였습니다.");
                const error = await res.json();
                throw new Error(error.message || "이벤트 생성 실패");
            }
            const data = await res.json();
            alert(data.name + " 이벤트가 생성되었습니다.");
            window.location.href="../admin_board.html"
        } catch (err) {
            alert("알 수 없는 오류가 발생했습니다. 나중에 다시 시도해주세요.");
            console.log(err);
        }
    })
})


