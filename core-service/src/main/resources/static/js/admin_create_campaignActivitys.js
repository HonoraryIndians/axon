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

    // --- Shared Module Initialization ---
    const { FilterRegistry, Helpers } = CampaignActivityModule;

    // 1. Image Upload
    let uploadedImageUrl = "";
    Helpers.initImageUpload(document, 'activityImage', 'imagePreview', 'imagePlaceholder', 'imageOverlay', 'uploadBtn', (url) => {
        uploadedImageUrl = url;
        updateBasicPreview();
    });

    // 2. Product Search
    Helpers.initProductSearch(document, 'productSearchModal', 'productSearchInput', 'productSearchResults', 'productSearchActionBtn', (product) => {
        document.getElementById('selectedProductId').value = product.id;
        document.getElementById('selectedProductName').value = product.name;
        document.getElementById('productOriginalPrice').textContent = `${product.price}원`;
        document.getElementById('productStock').textContent = `${product.stock}개`;
        document.getElementById('productInfo').classList.remove('hidden');
        updateBasicPreview();
    });

    // 3. Filters
    const filterContainer = document.getElementById("filter-container");
    const dynamicFilterContainer = document.getElementById("dynamic-filter-container");

    // Init Static Filters
    FilterRegistry.initStaticFilters(document);

    // Init Dynamic Filter Button
    const addFilterBtn = document.getElementById('add-filter-btn');
    const addFilterMenu = document.getElementById('add-filter-menu');

    if (addFilterBtn && addFilterMenu) {
        addFilterBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            addFilterMenu.classList.toggle('hidden');
        });

        document.addEventListener('click', () => addFilterMenu.classList.add('hidden'));

        addFilterMenu.querySelectorAll('[data-add-filter]').forEach(btn => {
            btn.addEventListener('click', () => {
                const type = btn.dataset.addFilter;
                FilterRegistry.addDynamicFilter(document, type);
                updateFilterPreview();
            });
        });
    }

    // Filter Change Listener for Preview
    if (filterContainer) {
        filterContainer.addEventListener("change", updateFilterPreview);
        filterContainer.addEventListener("input", updateFilterPreview);

        // Observer for dynamic changes
        const observer = new MutationObserver(updateFilterPreview);
        observer.observe(filterContainer, { childList: true, subtree: true });
        if (dynamicFilterContainer) observer.observe(dynamicFilterContainer, { childList: true, subtree: true });
    }


    // --- Basic Logic ---
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
                if (defaultOption) defaultOption.textContent = "캠페인을 선택하지 못했습니다.";
            });
    }
    loadCampaigns();

    const selectableTypes = document.querySelectorAll(".campaign-type-card.selectable");
    selectableTypes.forEach(button => {
        button.addEventListener("click", () => {
            selectableTypes.forEach(btn => btn.classList.remove("selected"));
            button.classList.add("selected");
            activityTypeInput.value = button.dataset.type;
            updateBasicPreview();
        });
    });

    const filterTypeCheckboxes = document.querySelectorAll(".filter-type-checkbox");
    filterTypeCheckboxes.forEach(checkbox => {
        checkbox.addEventListener("change", () => {
            const filterType = checkbox.dataset.filterType;
            const details = document.querySelector(`.filter-details[data-filter-details="${filterType}"]`);
            if (!details) return;
            details.classList.toggle("hidden", !checkbox.checked);
            if (!checkbox.checked) {
                details.querySelectorAll("input, select").forEach(input => {
                    if (input.type === "checkbox" || input.type === "radio") input.checked = false;
                    else input.value = "";
                });
            }
            updateFilterPreview();
        });
    });

    // --- Preview Logic ---
    const previewElements = {
        card: {
            badge: document.getElementById("preview-card-badge"),
            title: document.getElementById("preview-card-title"),
            icon: document.getElementById("preview-card-icon")
        },
        detail: {
            title: document.getElementById("preview-detail-title"),
            limit: document.getElementById("preview-detail-limit"),
            icon: document.getElementById("preview-detail-icon")
        },
        filter: {
            container: document.getElementById("preview-filter-container"),
            list: document.getElementById("preview-filter-list")
        }
    };

    const inputs = {
        name: document.getElementById("activityName"),
        limit: document.getElementById("limitCountInput"),
        type: document.getElementById("activityTypeInput")
    };

    function updateBasicPreview() {
        const name = inputs.name?.value || "활동 이름";
        const limit = inputs.limit?.value ? `${inputs.limit.value}명` : "00명";
        const type = inputs.type?.value || "FIRST_COME_FIRST_SERVE";

        const originalPriceVal = document.getElementById('productOriginalPrice')?.textContent.replace(/[^0-9]/g, '') || '0';
        const salePriceVal = document.getElementById('salePrice')?.value || '0';
        const originalPrice = parseInt(originalPriceVal);
        const salePrice = parseInt(salePriceVal);

        if (previewElements.card.title) previewElements.card.title.textContent = name;
        if (previewElements.detail.title) previewElements.detail.title.textContent = name;
        if (previewElements.detail.limit) previewElements.detail.limit.textContent = limit.replace("명", "개");

        const detailOriginalPrice = document.querySelector('#preview-detail-title').parentElement.nextElementSibling.querySelector('span');
        const detailSalePrice = document.querySelector('#preview-detail-title').parentElement.nextElementSibling.nextElementSibling.querySelector('span');

        if (detailOriginalPrice) detailOriginalPrice.textContent = originalPrice > 0 ? `${originalPrice.toLocaleString()}원` : '?원';
        if (detailSalePrice) detailSalePrice.textContent = salePrice > 0 ? `${salePrice.toLocaleString()}원` : '?원';

        let typeLabel = "선착순";
        if (type === "COUPON") typeLabel = "쿠폰";
        else if (type === "GIVEAWAY") typeLabel = "응모/추첨";

        if (previewElements.card.badge) previewElements.card.badge.textContent = `${typeLabel} ${limit}`;

        let iconClass = "ph-gift";
        if (type === "FIRST_COME_FIRST_SERVE") iconClass = "ph-stopwatch";
        else if (type === "COUPON") iconClass = "ph-ticket";

        // Image Handling for Preview
        const updateImageOrIcon = (imgContainer, iconEl) => {
            if (!imgContainer) return;

            // Remove existing image if any
            const existingImg = imgContainer.querySelector('.preview-image');
            if (existingImg) existingImg.remove();

            if (uploadedImageUrl) {
                // Show Image
                if (iconEl) iconEl.classList.add('hidden');
                const img = document.createElement('img');
                img.src = uploadedImageUrl;
                img.className = "w-full h-full object-cover preview-image";
                imgContainer.appendChild(img);
                imgContainer.classList.remove('bg-white', 'bg-[#f8f8f8]'); // Remove background if needed
                imgContainer.classList.add('overflow-hidden');
            } else {
                // Show Icon
                if (iconEl) {
                    iconEl.classList.remove('hidden');
                    iconEl.className = `ph ${iconClass} text-3xl text-dark-gray`;
                    // Adjust icon size for detail view
                    if (iconEl.id === 'preview-detail-icon') {
                        iconEl.className = `ph ${iconClass} text-5xl text-dark-gray`;
                    }
                }
                imgContainer.classList.add('bg-white');
                imgContainer.classList.remove('overflow-hidden');
            }
        };

        // Card Preview Image Area
        const cardIconContainer = previewElements.card.icon?.parentElement;
        updateImageOrIcon(cardIconContainer, previewElements.card.icon);

        // Detail Preview Image Area
        const detailIconContainer = previewElements.detail.icon?.parentElement;
        updateImageOrIcon(detailIconContainer, previewElements.detail.icon);
    }

    function updateFilterPreview() {
        const container = previewElements.filter.container;
        const list = previewElements.filter.list;
        if (!container || !list) return;

        if (typeof common === 'undefined') return;

        list.innerHTML = "";
        const wrapper = document.querySelector('.lg\\:col-span-8'); // Main form column
        // Use silent mode for preview to avoid alerts while typing
        const filters = FilterRegistry.extractAll(wrapper, { silent: true });

        if (filters && filters.length > 0) {
            container.classList.remove("hidden");
            filters.forEach(filter => {
                const li = document.createElement("li");
                li.className = "flex items-start gap-2 text-sm text-gray-600";
                li.innerHTML = `<i class="ph-bold ph-dot text-gray-400 mt-1"></i> <span>${common.formatCondition(filter)}</span>`;
                list.appendChild(li);
            });
        } else {
            container.classList.add("hidden");
        }
    }

    if (inputs.name) inputs.name.addEventListener("input", updateBasicPreview);
    if (inputs.limit) inputs.limit.addEventListener("input", updateBasicPreview);
    const salePriceInput = document.getElementById('salePrice');
    if (salePriceInput) salePriceInput.addEventListener('input', updateBasicPreview);

    updateBasicPreview();
    updateFilterPreview();

    // --- Submit Logic ---
    if (submit) {
        submit.addEventListener("click", async () => {
            const campaignId = document.getElementById("campaignId").value;
            const name = document.getElementById("activityName").value;
            const type = document.getElementById("activityTypeInput").value;
            const startDate = document.getElementById("startDate").value;
            const endDate = document.getElementById("endDate").value;
            const limitCount = document.getElementById("limitCountInput").value;
            const productId = document.getElementById("selectedProductId").value;
            const salePrice = document.getElementById("salePrice").value;
            const saleQuantity = document.getElementById("saleQuantity").value;

            if (!campaignId || !name || !type || !startDate || !endDate || !limitCount) {
                alert("필수 항목을 모두 입력해주세요.");
                return;
            }

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

            const wrapper = document.querySelector('.lg\\:col-span-8');
            const filters = FilterRegistry.extractAll(wrapper);

            if (filters === null) return; // Validation error occurred in filters

            const payload = {
                name: name,
                activityType: type,
                startDate: startDate,
                endDate: endDate,
                limitCount: parseInt(limitCount),
                filters: filters,
                imageUrl: uploadedImageUrl,
                productId: productId ? parseInt(productId) : null,
                price: salePrice ? parseInt(salePrice) : 0,
                quantity: saleQuantity ? parseInt(saleQuantity) : 0,
                status: "DRAFT"
            };

            try {
                const response = await fetch(`/api/v1/campaign/${campaignId}/activities`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(payload)
                });

                if (response.ok) {
                    alert("캠페인 활동이 성공적으로 생성되었습니다.");
                    window.location.href = "/admin_board";
                } else {
                    const errorText = await response.text();
                    console.log(errorText);
                    alert("오류가 발생하여 생성에 실패하였습니다.");
                }
            } catch (error) {
                console.error("Error:", error);
                alert("오류가 발생했습니다.");
            }
        });
    }
});