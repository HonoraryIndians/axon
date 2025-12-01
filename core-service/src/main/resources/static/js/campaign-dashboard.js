document.addEventListener('DOMContentLoaded', function () {
    const campaignId = document.getElementById('campaignId').value;
    if (!campaignId) {
        console.error("Campaign ID not found");
        return;
    }

    // Initial fetch for immediate display
    fetchCampaignData(campaignId);

    // Start SSE for real-time updates
    connectToSse(campaignId);
});

async function fetchCampaignData(campaignId) {
    try {
        const response = await fetch(`/api/v1/dashboard/campaign/${campaignId}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        updateDashboard(data);
    } catch (error) {
        console.error("Error fetching campaign data:", error);
    }
}

function connectToSse(campaignId) {
    const eventSource = new EventSource(`/api/v1/dashboard/stream/campaign/${campaignId}`);

    eventSource.addEventListener('dashboard-update', function(event) {
        try {
            const data = JSON.parse(event.data);
            updateDashboard(data);
        } catch (error) {
            console.error("Error parsing SSE data:", error);
        }
    });

    eventSource.onerror = function(error) {
        console.error("SSE error:", error);
        eventSource.close();
        // Optional: Reconnect logic with backoff
        setTimeout(() => connectToSse(campaignId), 5000);
    };
}

function updateDashboard(data) {
    // 1. Update Header
    const headerEl = document.getElementById('campaignName');
    if(headerEl.textContent !== `Campaign Dashboard: ${data.campaignName}`) {
        headerEl.textContent = `Campaign Dashboard: ${data.campaignName}`;
    }
    document.getElementById('lastUpdated').textContent = `Last updated: ${new Date().toLocaleTimeString()}`;

    // 2. Update KPI Cards
    updateKpi('totalVisits', data.overview.totalVisits);
    updateKpi('totalPurchases', data.overview.purchaseCount);
    updateKpi('totalGMV', formatCurrency(data.overview.gmv));
    updateKpi('totalROAS', formatNumber(data.overview.roas) + '%');

    // 3. Render Comparison Chart (Smooth Update)
    renderComparisonChart(data.activities);

    // 4. Render Heatmap Chart (Smooth Update)
    renderHeatmapChart(data.heatmap);

    // 5. Update Activity Table
    updateActivityTable(data.activities);
}

function updateKpi(elementId, value) {
    const el = document.getElementById(elementId);
    if (el) {
        // Only update if value changed to avoid minor DOM thrashing (though browser handles this well)
        if (el.textContent !== String(value)) {
            el.textContent = value;
        }
    }
}

let comparisonChart = null;
function renderComparisonChart(activities) {
    const ctx = document.getElementById('comparisonChart').getContext('2d');
    
    const labels = activities.map(a => a.activityName);
    const visits = activities.map(a => a.totalVisits);
    const purchases = activities.map(a => a.totalPurchases);

    if (comparisonChart) {
        // Update existing chart data without destroying
        comparisonChart.data.labels = labels;
        comparisonChart.data.datasets[0].data = visits;
        comparisonChart.data.datasets[1].data = purchases;
        comparisonChart.update(); // Smooth transition
    } else {
        // Create new chart
        comparisonChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Visits',
                        data: visits,
                        backgroundColor: 'rgba(54, 162, 235, 0.6)',
                        borderColor: 'rgba(54, 162, 235, 1)',
                        borderWidth: 1
                    },
                    {
                        label: 'Purchases',
                        data: purchases,
                        backgroundColor: 'rgba(75, 192, 192, 0.6)',
                        borderColor: 'rgba(75, 192, 192, 1)',
                        borderWidth: 1
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: {
                    duration: 500 // Smooth animation
                },
                scales: {
                    y: {
                        beginAtZero: true
                    }
                },
                plugins: {
                    tooltip: {
                        mode: 'index',
                        intersect: false
                    }
                }
            }
        });
    }
}

let heatmapChart = null;
function renderHeatmapChart(heatmapData) {
    const ctx = document.getElementById('heatmapChart').getContext('2d');
    
    const hours = Array.from({length: 24}, (_, i) => i);
    const data = hours.map(h => heatmapData.hourlyTraffic[h] || 0);

    if (heatmapChart) {
        // Update existing chart
        heatmapChart.data.datasets[0].data = data;
        heatmapChart.update();
    } else {
        // Create new chart
        heatmapChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: hours.map(h => `${h}:00`),
                datasets: [{
                    label: 'Hourly Traffic',
                    data: data,
                    fill: true,
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    borderColor: 'rgba(255, 99, 132, 1)',
                    tension: 0.4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: {
                    duration: 500
                },
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

function updateActivityTable(activities) {
    const tbody = document.getElementById('activityTableBody');
    
    // Simple Strategy: Clear and Rebuild (Good enough for small N)
    // Optimization: If performance is an issue with 50+ rows, we can update individual cells
    // But for < 50 rows, innerHTML replacement is instant.
    
    tbody.innerHTML = '';

    activities.forEach(activity => {
        const row = document.createElement('tr');
        row.className = "bg-white border-b hover:bg-gray-50 transition-colors duration-150";
        row.innerHTML = `
            <td class="px-6 py-4 font-medium text-gray-900 whitespace-nowrap">${activity.activityName}</td>
            <td class="px-6 py-4 text-right">${formatNumber(activity.totalVisits)}</td>
            <td class="px-6 py-4 text-right">${formatNumber(activity.totalEngages)}</td>
            <td class="px-6 py-4 text-right font-semibold text-blue-600">${formatNumber(activity.ctr)}%</td>
            <td class="px-6 py-4 text-right">${formatNumber(activity.totalPurchases)}</td>
            <td class="px-6 py-4 text-right font-semibold text-green-600">${formatNumber(activity.conversionRate)}%</td>
            <td class="px-6 py-4 text-right">${formatCurrency(activity.gmv)}</td>
        `;
        tbody.appendChild(row);
    });
}

// Utility functions
function formatNumber(num) {
    if (num === undefined || num === null) return '-';
    return new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 1 }).format(num);
}

function formatCurrency(amount) {
    if (amount === undefined || amount === null) return '-';
    return new Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' }).format(amount);
}