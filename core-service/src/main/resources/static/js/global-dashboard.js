document.addEventListener('DOMContentLoaded', function () {
    const campaignId = document.getElementById('campaignId').value; // Will be "global"
    let topCampaignsChart = null;
    let globalTrafficChart = null;

    initGlobalDashboard();

    function initGlobalDashboard() {
        fetchGlobalDashboardData();
        // Poll every 10 seconds for updates (global can be less frequent)
        setInterval(fetchGlobalDashboardData, 10000);
    }

    async function fetchGlobalDashboardData() {
        try {
            const response = await fetch(`/api/v1/dashboard/overview`);
            const data = await response.json();
            updateDashboard(data);
        } catch (error) {
            console.error('Error fetching global dashboard data:', error);
        }
    }

    function updateDashboard(data) {
        updateOverviewCards(data.overview);
        renderTopCampaignsChart(data.topGmvCampaigns);
        renderGlobalTrafficChart(data.hourlyTraffic);
        updateCampaignEfficiencyTable(data.campaignEfficiency);
        updateLastUpdatedTime();
    }

    function updateOverviewCards(overview) {
        updateKpi('totalVisits', overview.totalVisits);
        updateKpi('totalPurchases', overview.purchaseCount);
        updateKpi('totalGMV', formatCurrency(overview.gmv));
        updateKpi('totalROAS', formatNumber(overview.roas) + '%');
    }

    function updateKpi(elementId, value) {
        const el = document.getElementById(elementId);
        if (el) {
            el.textContent = value;
        }
    }

    let topGmvCampaignsChart = null;
    function renderTopCampaignsChart(campaigns) {
        const ctx = document.getElementById('topCampaignsChart').getContext('2d');
        const labels = campaigns.map(c => c.campaignName);
        const data = campaigns.map(c => c.value); // Changed from c.gmv to c.value

        if (topGmvCampaignsChart) {
            topGmvCampaignsChart.data.labels = labels;
            topGmvCampaignsChart.data.datasets[0].data = data;
            topGmvCampaignsChart.update();
        } else {
            topGmvCampaignsChart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'GMV (KRW)',
                        data: data,
                        backgroundColor: '#3b82f6', // Blue-500
                        borderRadius: 4,
                        barPercentage: 0.6
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true,
                            grid: { color: '#f1f5f9', drawBorder: false },
                            ticks: {
                                callback: function (value) { return formatCurrency(value); },
                                font: { size: 11 },
                                color: '#64748b'
                            }
                        },
                        x: {
                            grid: { display: false },
                            ticks: { font: { size: 11 }, color: '#64748b' }
                        }
                    },
                    interaction: {
                        mode: 'index',
                        intersect: false,
                    },
                    plugins: {
                        tooltip: {
                            backgroundColor: '#1e293b',
                            padding: 12,
                            cornerRadius: 8,
                            titleFont: { size: 13, weight: 600 },
                            bodyFont: { size: 12 },
                            callbacks: {
                                label: function (context) {
                                    return context.dataset.label + ': ' + formatCurrency(context.raw);
                                }
                            }
                        },
                        legend: { display: false }
                    },
                    onClick: (evt, activeElements) => {
                        if (activeElements.length > 0) {
                            const index = activeElements[0].index;
                            const campaignId = campaigns[index].campaignId;
                            window.location.href = `/admin/dashboard/campaign/${campaignId}`;
                        }
                    },
                    onHover: (event, chartElement) => {
                        event.native.target.style.cursor = chartElement[0] ? 'pointer' : 'default';
                    }
                }
            });
        }
    }

    function renderGlobalTrafficChart(hourlyTraffic) {
        const ctx = document.getElementById('globalTrafficChart').getContext('2d');
        const hours = Array.from({ length: 24 }, (_, i) => i);
        const data = hours.map(h => hourlyTraffic.hourlyTraffic[h] || 0);

        if (globalTrafficChart) {
            globalTrafficChart.data.datasets[0].data = data;
            globalTrafficChart.update();
        } else {
            globalTrafficChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: hours.map(h => `${h}:00`),
                    datasets: [{
                        label: 'Hourly Visitors',
                        data: data,
                        fill: {
                            target: 'origin',
                            above: 'rgba(59, 130, 246, 0.1)'
                        },
                        borderColor: '#60a5fa', // Blue-400
                        borderWidth: 2,
                        tension: 0.4,
                        pointRadius: 0,
                        pointHoverRadius: 4
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true,
                            grid: { color: '#f1f5f9', drawBorder: false },
                            title: { display: true, text: 'Visitors' },
                            ticks: { color: '#64748b' }
                        },
                        x: {
                            grid: { display: false },
                            ticks: { color: '#64748b' }
                        }
                    },
                    plugins: {
                        tooltip: {
                            backgroundColor: '#1e293b',
                            padding: 10
                        },
                        legend: { display: false }
                    }
                }
            });
        }
    }

    function updateCampaignEfficiencyTable(campaigns) {
        const tbody = document.getElementById('campaignEfficiencyTableBody');
        tbody.innerHTML = ''; // Clear existing rows

        campaigns.forEach(campaign => {
            const row = document.createElement('tr');
            row.className = "bg-white border-b hover:bg-gray-50 transition-colors duration-150";
            row.innerHTML = `
                <td class="px-6 py-4 font-medium text-gray-900 whitespace-nowrap">
                    <a href="/admin/dashboard/campaign/${campaign.campaignId}" class="text-blue-600 hover:underline hover:text-blue-800 transition-colors">
                        ${campaign.campaignName}
                    </a>
                </td>
                <td class="px-6 py-4 text-right">${formatCurrency(campaign.budget)}</td>
                <td class="px-6 py-4 text-right">${formatCurrency(campaign.gmv)}</td>
                <td class="px-6 py-4 text-right font-semibold text-blue-600">${formatNumber(campaign.roas)}%</td>
            `;
            tbody.appendChild(row);
        });
    }

    function updateLastUpdatedTime() {
        const now = new Date();
        const timeString = now.toLocaleTimeString();
        const el = document.getElementById('lastUpdated');
        if (el) el.textContent = `Last updated: ${timeString}`;
    }

    // Utility functions (copied from dashboard.js for now, centralize later)
    function formatNumber(num) {
        if (num === undefined || num === null) return '-';
        return new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(num);
    }

    function formatCurrency(amount) {
        if (amount === undefined || amount === null) return '-';
        return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount);
    }
});