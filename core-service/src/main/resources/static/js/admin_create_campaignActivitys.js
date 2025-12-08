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


    // --- Dynamic Form Logic ---
    const imageUploadSection = document.getElementById("image-upload-section");
    const previewSection = document.getElementById("preview-section"); // From HTML update

    // Product vs Coupon Containers
    const productInfoContainer = document.getElementById("product-info-container");
    const couponInfoContainer = document.getElementById("coupon-info-container");
    const section4Title = document.getElementById("section-4-title");

    // Elements to Clear
    const productInputs = [
        document.getElementById("selectedProductId"),
        document.getElementById("selectedProductName"),
        document.getElementById("salePrice"),
        document.getElementById("saleQuantity")
    ];
    const couponInputs = [
        document.getElementById("selectedCouponId"),
        document.getElementById("selectedCouponName")
    ];

    function toggleActivityTypeUI(type) {
        if (type === "COUPON") {
            // Show Coupon UI
            if (imageUploadSection) imageUploadSection.classList.add("hidden");
            if (previewSection) previewSection.classList.add("hidden");

            productInfoContainer.classList.add("hidden");
            couponInfoContainer.classList.remove("hidden");
            section4Title.textContent = "쿠폰 정보";

            // Clear Product Info
            productInputs.forEach(input => {
                if (input) {
                    input.value = "";
                    if (input.tagName === 'SPAN') input.textContent = "-";
                }
            });
            document.getElementById('productStock').textContent = '-';
            document.getElementById('productOriginalPrice').textContent = '-';
            document.getElementById('productInfo').classList.add('hidden');

        } else {
            // Show FCFS/Default UI
            if (imageUploadSection) imageUploadSection.classList.remove("hidden");
            if (previewSection) previewSection.classList.remove("hidden");

            couponInfoContainer.classList.add("hidden");
            productInfoContainer.classList.remove("hidden");
            section4Title.textContent = "상품 정보";

            // Clear Coupon Info
            couponInputs.forEach(input => {
                if (input) input.value = "";
            });
        }
    }

    // --- Coupon Search Logic ---
    const couponSearchModal = document.getElementById("couponSearchModal");
    const openCouponSearchBtn = document.getElementById("openCouponSearchBtn");
    const closeCouponSearchBtn = document.getElementById("closeCouponSearchBtn");
    const couponSearchResults = document.getElementById("couponSearchResults");
    const couponSearchInput = document.getElementById("couponSearchInput");
    const couponSearchActionBtn = document.getElementById("couponSearchActionBtn");

    let loadedCoupons = []; // Cache loaded coupons

    if (openCouponSearchBtn) {
        openCouponSearchBtn.addEventListener("click", () => {
            couponSearchModal.classList.remove("hidden");
            if (couponSearchInput) couponSearchInput.value = ''; // Clear search
            fetchAndRenderCoupons();
        });
    }

    if (closeCouponSearchBtn) {
        closeCouponSearchBtn.addEventListener("click", () => {
            couponSearchModal.classList.add("hidden");
        });
    }

    // Search Action
    if (couponSearchActionBtn && couponSearchInput) {
        couponSearchActionBtn.addEventListener("click", () => {
            renderCouponList(couponSearchInput.value);
        });
        couponSearchInput.addEventListener("keyup", (e) => {
            if (e.key === "Enter") {
                renderCouponList(couponSearchInput.value);
            }
        });
    }

    // Close on background click
    if (couponSearchModal) {
        couponSearchModal.addEventListener('click', (e) => {
            if (e.target === couponSearchModal) {
                couponSearchModal.classList.add('hidden');
            }
        });
    }

    function fetchAndRenderCoupons() {
        fetch('/api/coupons')
            .then(res => res.json())
            .then(coupons => {
                loadedCoupons = coupons;
                renderCouponList();
            })
            .catch(err => {
                console.error("Error fetching coupons:", err);
                couponSearchResults.innerHTML = '<li class="p-4 text-center text-red-500">쿠폰 목록을 불러오지 못했습니다.</li>';
            });
    }

    function renderCouponList(query = "") {
        couponSearchResults.innerHTML = '';
        const filtered = loadedCoupons.filter(c => c.couponName.toLowerCase().includes(query.toLowerCase()));

        if (filtered.length === 0) {
            couponSearchResults.innerHTML = '<li class="p-4 text-center text-gray-500">검색 결과가 없습니다.</li>';
            return;
        }

        filtered.forEach(coupon => {
            const li = document.createElement('li');
            li.className = 'p-4 hover:bg-gray-50 cursor-pointer flex justify-between items-center transition-colors';

            let benefit = '';
            if (coupon.discountAmount) benefit = `${Number(coupon.discountAmount).toLocaleString()}원 할인`;
            else if (coupon.discountRate) benefit = `${coupon.discountRate}% 할인`;

            li.innerHTML = `
                <div>
                    <div class="font-medium text-gray-900">${coupon.couponName}</div>
                    <div class="text-xs text-gray-500">${benefit} | ${new Date(coupon.startDate).toLocaleDateString()} ~ ${new Date(coupon.endDate).toLocaleDateString()}</div>
                </div>
                <button type="button" class="text-xs bg-purple-100 text-purple-700 px-3 py-1 rounded-full font-bold">선택</button>
            `;

            li.addEventListener('click', () => {
                document.getElementById('selectedCouponId').value = coupon.id;
                document.getElementById('selectedCouponName').value = coupon.couponName;
                couponSearchModal.classList.add('hidden');
            });

            couponSearchResults.appendChild(li);
        });
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
            const type = button.dataset.type;
            activityTypeInput.value = type;

            toggleActivityTypeUI(type); // Trigger toggle logic
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
        if (inputs.type?.value === 'COUPON') return; // Skip preview update for Coupon

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

            // Product or Coupon Data handling
            const productId = document.getElementById("selectedProductId").value;
            const salePrice = document.getElementById("salePrice").value;
            const saleQuantity = document.getElementById("saleQuantity").value;
            const couponId = document.getElementById("selectedCouponId").value;

            const budget = document.getElementById("budgetInput").value;

            if (!campaignId || !name || !type || !startDate || !endDate || !limitCount) {
                alert("필수 항목을 모두 입력해주세요.");
                return;
            }

            if (new Date(startDate) > new Date(endDate)) {
                alert("종료 일시는 시작 일시보다 뒤여야 합니다.");
                return;
            }

            // Validation Per Type
            let finalProductId = null;
            let finalCouponId = null;
            let finalPrice = 0;
            let finalQuantity = 0;

            if (type === 'COUPON') {
                if (!couponId) {
                    alert("연동할 쿠폰을 선택해주세요.");
                    return;
                }
                finalCouponId = parseInt(couponId);
                // Price and Quantity are 0 for types other than FCFS usually, or handled differently.
                // Request mandates 0
            } else {
                if (productId) {
                    finalProductId = parseInt(productId);
                    if (!salePrice || parseInt(salePrice) < 0) {
                        alert("상품 판매 가격은 0원 이상이어야 합니다.");
                        return;
                    }
                    if (!saleQuantity || parseInt(saleQuantity) <= 0) {
                        alert("상품 판매 수량은 1개 이상이어야 합니다.");
                        return;
                    }
                    finalPrice = parseInt(salePrice);
                    finalQuantity = parseInt(saleQuantity);
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
                imageUrl: (type === 'COUPON') ? null : uploadedImageUrl, // No image for coupon
                productId: finalProductId,
                couponId: finalCouponId,
                price: finalPrice,
                quantity: finalQuantity,
                budget: budget ? parseInt(budget) : 0,
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
                    window.location.href = "/admin/campaigns";
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