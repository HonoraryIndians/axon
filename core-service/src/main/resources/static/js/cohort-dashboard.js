document.addEventListener('DOMContentLoaded', function () {
    const activityId = document.getElementById('activityId').value;
    let ltvGrowthChart = null;

    // Initialize Cohort Dashboard
    initCohortDashboard();

    function initCohortDashboard() {
        fetchCohortData();
    }

    async function fetchCohortData() {
        try {
            const response = await fetch(`/api/v1/dashboard/cohort/activity/${activityId}`);
            const data = await response.json();

            updateCohortInfo(data);
            updateLTVMetrics(data);
            updateBehaviorMetrics(data);
            renderLTVGrowthChart(data);
            updateRatioInterpretation(data);
            updateCalculatedTime(data.calculatedAt);
        } catch (error) {
            console.error('Error fetching cohort data:', error);
            showErrorMessage();
        }
    }

    function updateCohortInfo(data) {
        // Cohort Period
        const startDate = new Date(data.cohortStartDate).toLocaleDateString('ko-KR');
        const endDate = data.cohortEndDate ? new Date(data.cohortEndDate).toLocaleDateString('ko-KR') : '현재';
        document.getElementById('cohortPeriod').textContent = `${startDate} ~ ${endDate}`;

        // Total Customers
        document.getElementById('totalCustomers').textContent = (data.totalCustomers || 0).toLocaleString();

        // Total Budget
        document.getElementById('totalBudget').textContent = formatCurrency(data.totalAcquisitionCost);

        // Avg CAC
        document.getElementById('avgCACBanner').textContent = formatCurrency(data.avgCAC);
    }

    function updateLTVMetrics(data) {
        // 30-day LTV
        document.getElementById('ltv30d').textContent = formatCurrency(data.ltv30d);
        document.getElementById('ratio30d').textContent = formatRatio(data.ltvCacRatio30d);

        // 90-day LTV
        document.getElementById('ltv90d').textContent = formatCurrency(data.ltv90d);
        document.getElementById('ratio90d').textContent = formatRatio(data.ltvCacRatio90d);

        // 365-day LTV
        document.getElementById('ltv365d').textContent = formatCurrency(data.ltv365d);
        document.getElementById('ratio365d').textContent = formatRatio(data.ltvCacRatio365d);

        // Current LTV
        document.getElementById('ltvCurrent').textContent = formatCurrency(data.ltvCurrent);
        document.getElementById('ratioCurrent').textContent = formatRatio(data.ltvCacRatioCurrent);
    }

    function updateBehaviorMetrics(data) {
        // Repeat Purchase Rate
        document.getElementById('repeatPurchaseRate').textContent =
            (data.repeatPurchaseRate || 0).toFixed(1) + '%';

        // Avg Purchase Frequency
        document.getElementById('avgPurchaseFrequency').textContent =
            (data.avgPurchaseFrequency || 0).toFixed(1) + '회';

        // Avg Order Value
        document.getElementById('avgOrderValue').textContent = formatCurrency(data.avgOrderValue);
    }

    function renderLTVGrowthChart(data) {
        const ctx = document.getElementById('ltvGrowthChart');
        if (!ctx) return;

        // Destroy existing chart
        if (ltvGrowthChart) {
            ltvGrowthChart.destroy();
        }

        const chartData = {
            labels: ['초기', '30일', '90일', '365일', '현재'],
            datasets: [
                {
                    label: 'LTV (₩)',
                    data: [
                        0, // Initial
                        data.ltv30d || 0,
                        data.ltv90d || 0,
                        data.ltv365d || 0,
                        data.ltvCurrent || 0
                    ],
                    borderColor: 'rgb(16, 185, 129)',
                    backgroundColor: 'rgba(16, 185, 129, 0.1)',
                    tension: 0.4,
                    fill: true
                },
                {
                    label: 'CAC (₩)',
                    data: [
                        data.avgCAC || 0,
                        data.avgCAC || 0,
                        data.avgCAC || 0,
                        data.avgCAC || 0,
                        data.avgCAC || 0
                    ],
                    borderColor: 'rgb(239, 68, 68)',
                    backgroundColor: 'rgba(239, 68, 68, 0.1)',
                    borderDash: [5, 5],
                    tension: 0,
                    fill: false
                }
            ]
        };

        ltvGrowthChart = new Chart(ctx, {
            type: 'line',
            data: chartData,
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: 'index',
                    intersect: false,
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top',
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                let label = context.dataset.label || '';
                                if (label) {
                                    label += ': ';
                                }
                                label += formatCurrency(context.parsed.y);
                                return label;
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            callback: function(value) {
                                return '₩' + (value / 1000000).toFixed(1) + 'M';
                            }
                        }
                    }
                }
            }
        });
    }

    function updateRatioInterpretation(data) {
        const ratio = data.ltvCacRatioCurrent || 0;
        const interpretationEl = document.getElementById('interpretationText');
        const containerEl = document.getElementById('currentRatioInterpretation');

        let interpretation = '';
        let bgColor = '';
        let borderColor = '';
        let textColor = '';

        if (ratio < 1.0) {
            interpretation = `LTV/CAC 비율이 ${ratio.toFixed(2)}로, 고객 획득 비용이 생애 가치보다 높습니다. 마케팅 전략을 재검토하거나, 재구매율 향상 프로그램을 도입하세요.`;
            bgColor = 'bg-red-50';
            borderColor = 'border-red-200';
            textColor = 'text-red-700';
        } else if (ratio >= 1.0 && ratio < 3.0) {
            interpretation = `LTV/CAC 비율이 ${ratio.toFixed(2)}로, 수익은 나고 있으나 마케팅 효율 개선이 필요합니다. 고객 유지율을 높이거나 획득 비용을 낮추는 전략을 고려하세요.`;
            bgColor = 'bg-yellow-50';
            borderColor = 'border-yellow-200';
            textColor = 'text-yellow-700';
        } else {
            interpretation = `LTV/CAC 비율이 ${ratio.toFixed(2)}로, 건강한 마케팅 효율을 보이고 있습니다! 공격적인 마케팅 확장과 신규 고객 유입 증대를 추진할 수 있습니다.`;
            bgColor = 'bg-green-50';
            borderColor = 'border-green-200';
            textColor = 'text-green-700';
        }

        interpretationEl.textContent = interpretation;
        interpretationEl.className = `font-semibold ${textColor}`;

        // Update container colors
        containerEl.className = `mt-4 p-4 ${bgColor} border ${borderColor} rounded-lg`;
    }

    function updateCalculatedTime(timestamp) {
        const el = document.getElementById('calculatedAt');
        if (!el || !timestamp) return;

        const date = new Date(timestamp);
        const formatted = date.toLocaleTimeString('ko-KR', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
        el.textContent = `Calculated: ${formatted}`;
    }

    function formatCurrency(value) {
        if (!value || value === 0) return '₩0';
        return '₩' + Math.round(value).toLocaleString();
    }

    function formatRatio(value) {
        if (!value || value === 0) return '0.00';
        return value.toFixed(2);
    }

    function showErrorMessage() {
        const main = document.querySelector('main');
        if (main) {
            main.innerHTML = `
                <div class="bg-red-50 border border-red-200 rounded-xl p-8 text-center">
                    <i class="fa-solid fa-exclamation-triangle text-red-600 text-4xl mb-4"></i>
                    <h3 class="text-xl font-semibold text-red-900 mb-2">데이터를 불러올 수 없습니다</h3>
                    <p class="text-red-700">Activity ID: ${activityId}에 대한 코호트 데이터가 없거나 오류가 발생했습니다.</p>
                    <a href="/admin/dashboard/${activityId}"
                       class="mt-4 inline-block px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                        실시간 대시보드로 돌아가기
                    </a>
                </div>
            `;
        }
    }
});
