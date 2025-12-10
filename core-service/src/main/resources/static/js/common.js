const common = (() => {
    /**
     * Retrieve the value of a named cookie from document.cookie.
     * @param {string} name - The cookie name to look up.
     * @returns {string|undefined} The decoded cookie value if found, `undefined` otherwise.
     */
    function getCookie(name) {
        const pattern =
            "(?:^|; )" + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, "\\$1") + "=([^;]*)";
        const match = document.cookie.match(new RegExp(pattern));
        return match ? decodeURIComponent(match[1]) : undefined;
    }

    // 필요한 함수만 외부에 노출
    return {
        getCookie,

        /**
         * Logout the current user by redirecting to Spring Security logout endpoint.
         * This will trigger CustomLogoutHandler which:
         * - Deletes user cache from Redis
         * - Publishes logout event
         * - Clears JWT cookies
         */
        logout: function () {
            window.location.href = '/logout';
        },

        /**
         * Format filter condition to human-readable string.
         * @param {Object} filter - Filter object {type, operator, values}
         * @returns {string} Formatted string
         */
        formatCondition: function (filter) {
            const typeMap = {
                'AGE': '나이',
                'REGION': '거주 지역',
                'GRADE': '회원 등급',
                'RECENT_PURCHASE': '최근 구매 이력'
            };

            const type = typeMap[filter.type] || filter.type;
            const values = filter.values || [];
            const value = values.length > 0 ? values[0] : '';
            const operator = filter.operator;

            let formattedValue = value;
            let suffix = '';

            // 날짜 포맷팅 (RECENT_PURCHASE 인 경우)
            if (filter.type === 'RECENT_PURCHASE' && value.includes('T')) {
                try {
                    const date = new Date(value);
                    formattedValue = date.toLocaleDateString();
                } catch (e) { }
            }

            // 연산자 처리
            if (filter.type === 'AGE') {
                if (operator === 'LTE') suffix = '이하';
                else if (operator === 'GTE') suffix = '이상';
                else if (operator === 'EQ') suffix = '';
                else if (operator === 'BETWEEN') {
                    const val2 = values.length > 1 ? values[1] : '';
                    formattedValue = `${value}세 ~ ${val2}`;
                    suffix = '사이';
                }
            } else if (filter.type === 'GRADE') {
                formattedValue = values.join(', ');
                if (operator === 'IN') suffix = '포함되는';
                else if (operator === 'NOT_IN') suffix = '미포함되는';
            } else if (filter.type === 'REGION') {
                formattedValue = values.join(', ');
                suffix = '인';
            }

            // 문장 구성
            if (filter.type === 'AGE') {
                if (operator === 'BETWEEN') {
                    return `<strong class="text-black">${type}</strong>가 <strong>${formattedValue}</strong> ${suffix}인 회원`;
                }
                return `<strong class="text-black">${type}</strong>가 <strong>${formattedValue}세</strong> ${suffix}인 회원`;
            } else if (filter.type === 'GRADE') {
                return `<strong class="text-black">${type}</strong>이 <strong>${formattedValue}</strong>에 ${suffix} 회원`;
            } else if (filter.type === 'REGION') {
                return `<strong class="text-black">${type}</strong>이 <strong>${formattedValue}</strong> ${suffix} 회원`;
            } else if (filter.type === 'RECENT_PURCHASE' || filter.type === 'PURCHASE') {
                const displayType = '최근 구매 이력';
                if (operator === 'BETWEEN') {
                    const val2 = values.length > 1 ? values[1] : '';
                    let date1 = value;
                    let date2 = val2;
                    try { date1 = new Date(value).toLocaleDateString(); } catch (e) { }
                    try { date2 = new Date(val2).toLocaleDateString(); } catch (e) { }
                    return `<strong class="text-black">${displayType}</strong>이 <strong>${date1} ~ ${date2}</strong> 사이인 회원`;
                } else if (operator === 'NOT_BETWEEN') {
                    const val2 = values.length > 1 ? values[1] : '';
                    let date1 = value;
                    let date2 = val2;
                    try { date1 = new Date(value).toLocaleDateString(); } catch (e) { }
                    try { date2 = new Date(val2).toLocaleDateString(); } catch (e) { }
                    return `<strong class="text-black">${displayType}</strong>이 <strong>${date1} ~ ${date2}</strong> 사이가 <span class="text-red-600 font-bold">아닌</span> 회원`;
                } else if (operator === 'GTE') {
                    let date = formattedValue;
                    try { date = new Date(value).toLocaleDateString(); } catch (e) { }
                    return `<strong class="text-black">${displayType}</strong>이 <strong>${date}</strong> 이후인 회원`;
                } else if (operator === 'LTE') {
                    let date = formattedValue;
                    try { date = new Date(value).toLocaleDateString(); } catch (e) { }
                    return `<strong class="text-black">${displayType}</strong>이 <strong>${date}</strong> 이전인 회원`;
                }
                return `<strong class="text-black">${displayType}</strong>: <strong>${formattedValue}</strong>`;
            } else {
                return `<strong class="text-black">${type}</strong>: <strong>${formattedValue}</strong> (${suffix})`;
            }
        },

        initTooltips: function () {
            if (typeof tippy === 'undefined') {
                console.warn('Tippy.js not loaded');
                return;
            }

            const tooltips = {
                'totalVisits': '기간 내 페이지 총 방문 횟수입니다.',
                'totalEngages': '페이지 내에서 발생한 의미 있는 상호작용(클릭 등) 횟수입니다.',
                'totalQualifies': '참여 자격 검증(나이, 등급 등)을 통과한 사용자 수입니다.',
                'totalPurchases': '실제 구매(결제)가 완료된 총 건수입니다.',
                'purchaseCount': '실제 구매(결제)가 완료된 총 건수입니다.',
                'totalGMV': '총 거래액 (Gross Merchandise Value)<br>= 판매 수량 × 상품 가격',
                'gmv': '총 거래액 (Gross Merchandise Value)<br>= 판매 수량 × 상품 가격',
                'totalROAS': '광고 비용 대비 수익률 (Return on Ad Spend)<br>= (GMV / 마케팅 예산) × 100%',
                'roas': '광고 비용 대비 수익률 (Return on Ad Spend)<br>= (GMV / 마케팅 예산) × 100%',
                'conversionRate': '방문 대비 구매 전환율 (CVR)<br>= (구매 건수 / 방문 수) × 100%',
                'engagementRate': '방문 대비 참여율 (CTR)<br>= (참여 건수 / 방문 수) × 100%',
                'aov': '평균 주문 금액 (Average Order Value)<br>= GMV / 구매 건수'
            };

            Object.keys(tooltips).forEach(id => {
                const targetId = id + '-tooltip-icon';
                const target = document.getElementById(targetId);
                if (target) {
                    tippy(target, {
                        content: tooltips[id],
                        allowHTML: true,
                        placement: 'top',
                        animation: 'shift-away',
                        zIndex: 99999,
                        appendTo: document.body
                    });
                }
            });

            // Initialize standard data-tippy-content attributes
            tippy('[data-tippy-content]', {
                allowHTML: true,
                placement: 'top',
                animation: 'shift-away',
                zIndex: 99999,
                appendTo: document.body
            });
        }
    };
})();

// Expose to window
window.common = common;

document.addEventListener('DOMContentLoaded', common.initTooltips);