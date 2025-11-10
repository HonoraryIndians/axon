document.addEventListener("DOMContentLoaded", () => {
    const submit = document.getElementById("submit");
    const token = common.getCookie("accessToken");

    if (!token) {
        alert("관리자 토큰이 필요합니다. 다시 로그인하세요.");
        window.location.href = "/";
        return;
    }

    const campaignSelect = document.getElementById("campaignId");
    const activityTypeInput = document.getElementById("activityTypeInput");

    function loadCampaigns() {
        fetch("/api/v1/campaign")
            .then(res => res.json())
            .then(campaigns => {
                campaigns.forEach(campaign => {
                    const option = new Option(campaign.name, campaign.id);
                    campaignSelect.appendChild(option);
                });
            })
            .catch(error => {
                console.error("캠페인을 불러오지 못했습니다.", error);
                const defaultOption = document.getElementById("cam_default");
                if (defaultOption) {
                    defaultOption.textContent = "캠페인을 선택하지 못했습니다.";
                }
            });
    }

    loadCampaigns();

    const selectableTypes = document.querySelectorAll(".campaign-type.selectable");
    selectableTypes.forEach(button => {
        button.addEventListener("click", () => {
            selectableTypes.forEach(btn => btn.classList.remove("selected"));
            button.classList.add("selected");
            activityTypeInput.value = button.dataset.type;
        });
    });

    const filterTypeCheckboxes = document.querySelectorAll(".filter-type-checkbox");
    filterTypeCheckboxes.forEach(checkbox => {
        checkbox.addEventListener("change", () => {
            const filterType = checkbox.dataset.filterType;
            const details = document.querySelector(`.filter-details[data-filter-details="${filterType}"]`);
            if (!details) return;

            if (checkbox.checked) {
                details.classList.remove("hidden");
            } else {
                details.classList.add("hidden");
                details.querySelectorAll("input, select").forEach(input => {
                    if (input.type === "checkbox" || input.type === "radio") {
                        input.checked = false;
                    } else {
                        input.value = "";
                    }
                });
            }
        });
    });

    const addAgeRangeBtn = document.getElementById("add-age-range-btn");
    const ageFilterInputs = document.getElementById("age-filter-inputs");

    if (addAgeRangeBtn && ageFilterInputs) {
        addAgeRangeBtn.addEventListener("click", () => {
            if (ageFilterInputs.children.length >= 10) return;
            const wrapper = document.createElement("div");
            wrapper.className = "flex items-center gap-2 mb-2";
            wrapper.innerHTML = `
                <input type="number" placeholder="20" class="form-input age-range-start w-20">
                ~
                <input type="number" placeholder="29" class="form-input age-range-end w-20">
                <button type="button" class="remove-age-range-btn text-red-500">-</button>
            `;
            ageFilterInputs.appendChild(wrapper);
        });

        ageFilterInputs.addEventListener("click", (event) => {
            if (event.target.classList.contains("remove-age-range-btn")) {
                event.target.parentElement.remove();
            }
        });
    }

    const sidoSelect = document.getElementById("region-sido-select");
    const sigunguSelect = document.getElementById("region-sigungu-select");
    const selectedRegionsContainer = document.getElementById("selected-regions-container");
    let districtData = {};

    if (sidoSelect && sigunguSelect && selectedRegionsContainer) {
        fetch("/data/korean-districts.json")
            .then(res => res.json())
            .then(data => {
                districtData = data;
                Object.keys(districtData).forEach(sido => {
                    sidoSelect.add(new Option(sido, sido));
                });
            })
            .catch(error => console.error("지역 데이터를 불러오지 못했습니다.", error));

        sidoSelect.addEventListener("change", () => {
            sigunguSelect.innerHTML = '<option value="">시/군/구 선택</option><option value="ALL">전체</option>';
            const selectedSido = sidoSelect.value;
            if (!selectedSido || !districtData[selectedSido]) return;

            districtData[selectedSido].forEach(sigungu => {
                sigunguSelect.add(new Option(sigungu, sigungu));
            });
        });

        sigunguSelect.addEventListener("change", () => {
            const sido = sidoSelect.value;
            const sigungu = sigunguSelect.value;
            if (!sido || !sigungu) return;

            const value = sigungu === "ALL" ? sido : `${sido}-${sigungu}`;
            const label = sigungu === "ALL" ? `${sido} 전체` : `${sido} ${sigungu}`;

            const exists = Array.from(selectedRegionsContainer.querySelectorAll(".selected-region-tag"))
                .some(tag => tag.dataset.region === value);
            if (exists) {
                alert("이미 추가된 지역입니다.");
                return;
            }

            const tag = document.createElement("span");
            tag.className = "selected-region-tag bg-gray-200 text-gray-800 text-sm font-medium mr-2 px-2.5 py-0.5 rounded";
            tag.dataset.region = value;
            tag.textContent = label;

            const removeBtn = document.createElement("button");
            removeBtn.type = "button";
            removeBtn.className = "ml-2 text-red-500";
            removeBtn.innerHTML = "&times;";
            removeBtn.addEventListener("click", () => tag.remove());

            tag.appendChild(removeBtn);
            selectedRegionsContainer.appendChild(tag);
        });
    }

    submit.addEventListener("click", async () => {
        const campaignId = document.getElementById("campaignId").value;
        const activityName = document.getElementById("activityName").value.trim();
        const activityType = activityTypeInput.value;
        const limitCount = document.getElementById("limitCountInput").value;
        const startDate = document.getElementById("startDate").value;
        const endDate = document.getElementById("endDate").value;
        const status = "DRAFT";

        if (!campaignId || !activityName || !activityType || !startDate || !endDate) {
            alert("필수 항목을 반드시 선택하세요.");
            return;
        }

        if (!limitCount || Number(limitCount) <= 0) {
            alert("최소 참여 인원은 1명입니다.");
            return;
        }

        const now = new Date();
        const start = new Date(startDate);
        const end = new Date(endDate);

        if (start <= now) {
            alert("시작 시간은 현재 시간보다 미래여야 합니다.");
            return;
        }
        if (end <= now) {
            alert("종료 시간은 현재 시간보다 미래여야 합니다.");
            return;
        }
        if (end <= start) {
            alert("종료 시간은 시작 시간보다 미래여야 합니다.");
            return;
        }

        const filters = [];
        const checkedGroups = document.querySelectorAll(".filter-type-checkbox:checked");
        let hasFilterValidationError = false;

        checkedGroups.forEach(groupCheckbox => {
            if (hasFilterValidationError) return;

            const type = groupCheckbox.dataset.filterType;
            let values = [];

            if (type === "AGE") {
                const ageRanges = document.querySelectorAll("#age-filter-inputs .flex");
                ageRanges.forEach(range => {
                    if (hasFilterValidationError) return;
                    const startAge = range.querySelector(".age-range-start")?.value;
                    const endAge = range.querySelector(".age-range-end")?.value;

                    if (!startAge || !endAge) {
                        alert("나이 범위를 모두 입력해주세요.");
                        hasFilterValidationError = true;
                        return;
                    }
                    if (Number(startAge) < 0 || Number(endAge) < 0) {
                        alert("나이는 0 이상이어야 합니다.");
                        hasFilterValidationError = true;
                        return;
                    }
                    if (Number(endAge) < Number(startAge)) {
                        alert("끝 나이는 시작 나이보다 크거나 같아야 합니다.");
                        hasFilterValidationError = true;
                        return;
                    }
                    values.push(`${startAge}-${endAge}`);
                });
            } else if (type === "REGION") {
                const regionTags = document.querySelectorAll(".selected-region-tag");
                if (regionTags.length === 0) {
                    alert("지역 필터를 선택했지만 지역을 추가하지 않았습니다.");
                    hasFilterValidationError = true;
                    return;
                }
                regionTags.forEach(tag => values.push(tag.dataset.region));
            } else {
                const valueCheckboxes = document.querySelectorAll(
                    `.filter-details[data-filter-details="${type}"] .filter-value-checkbox:checked`
                );
                if (valueCheckboxes.length === 0) {
                    alert("선택한 필터에 대한 값을 하나 이상 선택하세요.");
                    hasFilterValidationError = true;
                    return;
                }
                values = Array.from(valueCheckboxes).map(cb => cb.value);
            }

            if (!hasFilterValidationError && values.length > 0) {
                filters.push({ type, values });
            }
        });

        if (hasFilterValidationError) {
            return;
        }

        const payload = {
            name: activityName,
            limitCount: Number(limitCount),
            status,
            startDate,
            endDate,
            activityType,
            filters
        };

        try {
            const response = await fetch(`/api/v1/campaign/${campaignId}/activities`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.message || "캠페인 활동 생성에 실패했습니다.");
            }

            const data = await response.json();
            alert(`${data.name} 캠페인 활동이 생성되었습니다.`);
            window.location.href = "../admin_board.html";
        } catch (error) {
            console.error("캠페인 활동 생성 오류:", error);
            alert("알 수 없는 오류가 발생했습니다. 나중에 다시 시도해주세요.");
        }
    });
});
