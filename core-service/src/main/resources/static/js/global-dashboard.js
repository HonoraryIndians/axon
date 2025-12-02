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
                        backgroundColor: 'rgba(75, 192, 192, 0.6)',
                        borderColor: 'rgba(75, 192, 192, 1)',
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true,
                            ticks: {
                                callback: function(value) {
                                    return formatCurrency(value);
                                }
                            }
                        }
                    },
                    plugins: {
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    return context.dataset.label + ': ' + formatCurrency(context.raw);
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    function renderGlobalTrafficChart(hourlyTraffic) {
        const ctx = document.getElementById('globalTrafficChart').getContext('2d');
        const hours = Array.from({length: 24}, (_, i) => i);
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
                        fill: true,
                        backgroundColor: 'rgba(54, 162, 235, 0.2)',
                        borderColor: 'rgba(54, 162, 235, 1)',
                        tension: 0.4
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true,
                            title: {
                                display: true,
                                text: 'Visitors'
                            }
                        }
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
                <td class="px-6 py-4 font-medium text-gray-900 whitespace-nowrap">${campaign.campaignName}</td>
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