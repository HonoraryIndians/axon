document.addEventListener('DOMContentLoaded', () => {
    fetchCoupons();
    setupEventListeners();
});

function setupEventListeners() {
    const createBtn = document.getElementById('create-coupon-btn');
    const modal = document.getElementById('create-coupon-modal');
    const closeBtn = document.getElementById('close-modal-btn');
    const cancelBtn = document.getElementById('cancel-create-btn');
    const form = document.getElementById('create-coupon-form');
    const saveBtn = document.getElementById('save-coupon-btn');

    // Open Modal (Create)
    createBtn.addEventListener('click', () => {
        form.reset();
        document.getElementById('couponId').value = ''; // Reset ID
        document.getElementById('modal-title').textContent = '새 쿠폰 만들기';
        document.getElementById('save-coupon-btn').textContent = '생성하기';
        document.getElementById('discount-amount-group').classList.remove('hidden');
        document.getElementById('discount-rate-group').classList.remove('hidden');
        document.getElementById('discountAmount').disabled = false;
        document.getElementById('discountRate').disabled = false;
        modal.classList.remove('hidden');
    });

    // Close Modal
    const closeModal = () => {
        modal.classList.add('hidden');
    };
    closeBtn.addEventListener('click', closeModal);
    cancelBtn.addEventListener('click', closeModal);

    // Toggle Discount Inputs (Optional UX improvement: disable one when other is filled)
    const amountInput = document.getElementById('discountAmount');
    const rateInput = document.getElementById('discountRate');

    amountInput.addEventListener('input', () => {
        if (amountInput.value) {
            rateInput.value = '';
            rateInput.disabled = true;
        } else {
            rateInput.disabled = false;
        }
    });

    rateInput.addEventListener('input', () => {
        if (rateInput.value) {
            amountInput.value = '';
            amountInput.disabled = true;
        } else {
            amountInput.disabled = false;
        }
    });

    // Save Coupon (Create or Update)
    saveBtn.addEventListener('click', async () => {
        if (!validateForm()) return;

        const couponId = document.getElementById('couponId').value;
        const isEdit = !!couponId;

        const formData = {
            couponName: document.getElementById('couponName').value,
            discountAmount: document.getElementById('discountAmount').value || null,
            discountRate: document.getElementById('discountRate').value || null,
            minOrderAmount: document.getElementById('minOrderAmount').value || null,
            targetCategory: document.getElementById('targetCategory').value || null,
            startDate: document.getElementById('startDate').value,
            endDate: document.getElementById('endDate').value
        };

        try {
            const url = isEdit ? `/api/coupons/${couponId}` : '/api/coupons';
            const method = isEdit ? 'PUT' : 'POST';

            const response = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(formData)
            });

            if (response.ok) {
                alert(isEdit ? '쿠폰이 수정되었습니다.' : '쿠폰이 생성되었습니다.');
                closeModal();
                fetchCoupons();
            } else {
                const errorText = await response.text();
                alert((isEdit ? '수정 실패: ' : '생성 실패: ') + errorText);
            }
        } catch (error) {
            console.error('Error saving coupon:', error);
            alert('오류가 발생했습니다.');
        }
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (!e.target.closest('.dropdown-btn') && !e.target.closest('.dropdown-menu')) {
            document.querySelectorAll('.dropdown-menu').forEach(menu => {
                menu.classList.add('hidden');
            });
        }
    });
}

function openEditModal(coupon) {
    const modal = document.getElementById('create-coupon-modal');
    const form = document.getElementById('create-coupon-form');

    // Reset and Populate
    form.reset();
    document.getElementById('couponId').value = coupon.id;
    document.getElementById('modal-title').textContent = '쿠폰 수정하기';
    document.getElementById('save-coupon-btn').textContent = '수정하기';

    document.getElementById('couponName').value = coupon.couponName;
    document.getElementById('minOrderAmount').value = coupon.minOrderAmount || '';
    document.getElementById('targetCategory').value = coupon.targetCategory || '';
    document.getElementById('startDate').value = coupon.startDate;
    document.getElementById('endDate').value = coupon.endDate;

    const amountInput = document.getElementById('discountAmount');
    const rateInput = document.getElementById('discountRate');

    if (coupon.discountAmount) {
        amountInput.value = coupon.discountAmount;
        rateInput.value = '';
        rateInput.disabled = true;
        amountInput.disabled = false;
    } else if (coupon.discountRate) {
        rateInput.value = coupon.discountRate;
        amountInput.value = '';
        amountInput.disabled = true;
        rateInput.disabled = false;
    } else {
        amountInput.disabled = false;
        rateInput.disabled = false;
    }

    modal.classList.remove('hidden');
}

function deleteCoupon(id) {
    if (!confirm('정말로 이 쿠폰을 삭제하시겠습니까?')) return;

    fetch(`/api/coupons/${id}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.ok) {
                alert('쿠폰이 삭제되었습니다.');
                fetchCoupons();
            } else {
                alert('삭제 실패');
            }
        })
        .catch(error => {
            console.error('Error deleting coupon:', error);
            alert('오류가 발생했습니다.');
        });
}

// Global Dropdown Logic
let activeDropdownCouponId = null;

function createGlobalDropdown() {
    let dropdown = document.getElementById('global-dropdown-menu');
    if (!dropdown) {
        dropdown = document.createElement('div');
        dropdown.id = 'global-dropdown-menu';
        dropdown.className = 'fixed bg-white rounded-md shadow-lg z-50 border border-gray-100 hidden';
        dropdown.style.width = '120px';
        document.body.appendChild(dropdown);

        // Close when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.dropdown-btn') && !e.target.closest('#global-dropdown-menu')) {
                dropdown.classList.add('hidden');
                activeDropdownCouponId = null;
            }
        });

        // Handle scroll to close/update position (simplified: close on scroll)
        window.addEventListener('scroll', () => {
            if (!dropdown.classList.contains('hidden')) {
                dropdown.classList.add('hidden');
            }
        }, true);
    }
    return dropdown;
}

window.toggleDropdown = function (id, btnElement) {
    const dropdown = createGlobalDropdown();

    // If clicking same button, toggle off
    if (activeDropdownCouponId === id && !dropdown.classList.contains('hidden')) {
        dropdown.classList.add('hidden');
        activeDropdownCouponId = null;
        return;
    }

    activeDropdownCouponId = id;
    const index = window.loadedCoupons.findIndex(c => c.id === id);

    // Populate Content
    dropdown.innerHTML = `
        <button onclick="handleEdit(${index})" class="block w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-t-md">
            <i class="fa-solid fa-pen-to-square mr-2"></i> 수정
        </button>
        <button onclick="handleDelete(${id})" class="block w-full text-left px-4 py-2 text-sm text-red-600 hover:bg-red-50 rounded-b-md">
            <i class="fa-solid fa-trash mr-2"></i> 삭제
        </button>
    `;

    // Calculate Position
    const rect = btnElement.getBoundingClientRect();
    dropdown.style.top = `${rect.bottom + window.scrollY + 5}px`;
    dropdown.style.left = `${rect.right - 120}px`; // Align right edge

    dropdown.classList.remove('hidden');
};

window.handleEdit = function (couponIdx) {
    const coupon = window.loadedCoupons[couponIdx];
    openEditModal(coupon);
    document.getElementById('global-dropdown-menu').classList.add('hidden');
};

window.handleDelete = function (id) {
    deleteCoupon(id);
    document.getElementById('global-dropdown-menu').classList.add('hidden');
};

function validateForm() {
    const name = document.getElementById('couponName').value.trim();
    const amount = document.getElementById('discountAmount').value;
    const rate = document.getElementById('discountRate').value;
    const minOrder = document.getElementById('minOrderAmount').value;
    const start = document.getElementById('startDate').value;
    const end = document.getElementById('endDate').value;

    if (!name) {
        alert('쿠폰 이름을 입력해주세요.');
        return false;
    }

    // Discount Validation
    if (!amount && !rate) {
        alert('할인 금액 또는 할인율 중 하나를 입력해주세요.');
        return false;
    }
    if (amount && (isNaN(amount) || Number(amount) < 0)) {
        alert('할인 금액은 0 이상의 숫자여야 합니다.');
        return false;
    }
    if (rate && (isNaN(rate) || Number(rate) < 0 || Number(rate) > 100)) {
        alert('할인율은 0과 100 사이의 숫자여야 합니다.');
        return false;
    }

    // Min Order Validation
    if (minOrder && (isNaN(minOrder) || Number(minOrder) < 0)) {
        alert('최소 주문 금액은 0 이상의 숫자여야 합니다.');
        return false;
    }

    // Date Validation
    if (!start || !end) {
        alert('시작 및 종료 날짜를 모두 설정해주세요.');
        return false;
    }
    if (new Date(end) <= new Date(start)) {
        alert('종료 날짜는 시작 날짜보다 이후여야 합니다.');
        return false;
    }

    return true;
}

async function fetchCoupons() {
    try {
        const response = await fetch('/api/coupons');
        const coupons = await response.json();
        renderCouponList(coupons);
    } catch (error) {
        console.error('Error fetching coupons:', error);
    }
}

function renderCouponList(coupons) {
    window.loadedCoupons = coupons;
    const tbody = document.getElementById('coupon-list-body');
    tbody.innerHTML = '';

    if (coupons.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="py-10 text-center text-gray-400">
                    등록된 쿠폰이 없습니다.
                </td>
            </tr>`;
        return;
    }

    coupons.forEach((coupon) => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-gray-50 relative';

        let typeIcon = '';
        let typeClass = '';
        let benefit = '';

        if (coupon.discountAmount) {
            typeIcon = '<i class="fa-solid fa-won-sign"></i>';
            typeClass = 'bg-blue-100 text-blue-700'; // Blue for Amount
            benefit = `${Number(coupon.discountAmount).toLocaleString()}원`;
        } else if (coupon.discountRate) {
            typeIcon = '<i class="fa-solid fa-percent"></i>';
            typeClass = 'bg-purple-100 text-purple-700'; // Purple for Rate
            benefit = `${coupon.discountRate}%`;
        }

        // Format Dates
        const start = new Date(coupon.startDate).toLocaleString();
        const end = new Date(coupon.endDate).toLocaleString();

        tr.innerHTML = `
            <td class="py-3 px-4 font-medium text-gray-900">${coupon.couponName}</td>
            <td class="py-3 px-4">
                <span class="inline-flex items-center justify-center w-8 h-8 rounded-full ${typeClass}">
                    ${typeIcon}
                </span>
            </td>
            <td class="py-3 px-4 text-gray-600 font-medium">${benefit}</td>
            <td class="py-3 px-4 text-gray-500 text-xs">${start} ~ <br>${end}</td>
            <td class="py-3 px-4 text-gray-400 text-xs">${start}</td>
            <td class="py-3 px-4">
                <button onclick="toggleDropdown(${coupon.id}, this)" class="dropdown-btn text-gray-400 hover:text-gray-600 p-1">
                    <i class="fa-solid fa-ellipsis-vertical"></i>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}
