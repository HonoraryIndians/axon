document.addEventListener('DOMContentLoaded', function () {
    fetchGlobalData();
    // Refresh every 30 seconds (Global dashboard doesn't need instant real-time)
    setInterval(fetchGlobalData, 30000);
});

async function fetchGlobalData() {
    try {
        const response = await fetch(`/api/v1/dashboard/overview`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        updateDashboard(data);
    } catch (error) {
        console.error("Error fetching global data:", error);
    }
}

function updateDashboard(data) {
    // 1. Update Header
    document.getElementById('lastUpdated').textContent = `Last updated: ${new Date().toLocaleTimeString()}`;

    // 2. Update KPI Cards
    updateKpi('totalVisits', formatNumber(data.totalOverview.totalVisits));
    updateKpi('totalPurchases', formatNumber(data.totalOverview.purchaseCount));
    updateKpi('totalGMV', formatCurrency(data.totalOverview.gmv));
    updateKpi('totalROAS', formatNumber(data.totalOverview.roas) + '%');

    // 3. Render Charts
    renderEfficiencyChart(data.efficiencyData);
    renderRankChart('gmvRankChart', data.topCampaignsByGmv, 'GMV', 'rgba(16, 185, 129, 0.7)');
    renderRankChart('visitRankChart', data.topCampaignsByVisits, 'Visits', 'rgba(59, 130, 246, 0.7)');
}

function updateKpi(elementId, value) {
    const el = document.getElementById(elementId);
    if (el) {
        el.textContent = value;
    }
}

let efficiencyChart = null;
function renderEfficiencyChart(data) {
    const ctx = document.getElementById('efficiencyChart').getContext('2d');
    
    const scatterData = data.map(item => ({
        x: item.budget, // X: Budget
        y: item.gmv,    // Y: GMV
        r: Math.max(5, Math.min(20, item.roas / 10)), // Radius based on ROAS
        campaignName: item.campaignName,
        roas: item.roas
    }));

    if (efficiencyChart) {
        efficiencyChart.data.datasets[0].data = scatterData;
        efficiencyChart.update();
    } else {
        efficiencyChart = new Chart(ctx, {
            type: 'bubble',
            data: {
                datasets: [{
                    label: 'Campaigns',
                    data: scatterData,
                    backgroundColor: (context) => {
                        const roas = context.raw?.roas || 0;
                        return roas > 200 ? 'rgba(16, 185, 129, 0.6)' : // High ROAS (Green)
                               roas > 100 ? 'rgba(245, 158, 11, 0.6)' : // Medium ROAS (Yellow)
                               'rgba(239, 68, 68, 0.6)';                // Low ROAS (Red)
                    },
                    borderColor: 'rgba(0, 0, 0, 0.1)',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const point = context.raw;
                                return `${point.campaignName}: Budget ₩${formatNumber(point.x)} -> GMV ₩${formatNumber(point.y)} (ROAS ${point.roas}%)`;
                            }
                        }
                    },
                    legend: { display: false }
                },
                scales: {
                    x: {
                        title: { display: true, text: 'Marketing Budget (₩)' },
                        beginAtZero: true
                    },
                    y: {
                        title: { display: true, text: 'Revenue (GMV ₩)' },
                        beginAtZero: true
                    }
                }
            }
        });
    }
}

const charts = {};
function renderRankChart(canvasId, data, label, color) {
    const ctx = document.getElementById(canvasId).getContext('2d');
    const labels = data.map(item => item.campaignName);
    const values = data.map(item => item.value);

    if (charts[canvasId]) {
        charts[canvasId].data.labels = labels;
        charts[canvasId].data.datasets[0].data = values;
        charts[canvasId].update();
    } else {
        charts[canvasId] = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: label,
                    data: values,
                    backgroundColor: color,
                    borderRadius: 4
                }]
            },
            options: {
                indexAxis: 'y', // Horizontal Bar Chart
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { beginAtZero: true }
                }
            }
        });
    }
}

// Utility functions
function formatNumber(num) {
    if (num === undefined || num === null) return '0';
    return new Intl.NumberFormat('ko-KR').format(num);
}

function formatCurrency(amount) {
    if (amount === undefined || amount === null) return '₩0';
    return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount);
}
