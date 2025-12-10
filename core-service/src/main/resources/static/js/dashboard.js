document.addEventListener('DOMContentLoaded', function () {
    const activityId = document.getElementById('activityId').value;
    let funnelChart = null;
    let trafficChart = null;

    // Initialize Dashboard
    initDashboard();

    function initDashboard() {
        fetchDashboardData();
        // Poll every 5 seconds for real-time updates
        setInterval(fetchRealtimeData, 5000);
    }

    async function fetchDashboardData() {
        try {
            const response = await fetch(`/api/v1/dashboard/activity/${activityId}?period=7d`);
            const data = await response.json();

            updateOverviewCards(data.overview, data.previousOverview);
            updateRealtimeStatus(data.realtime);
            renderFunnelChart(data.funnel);
            renderTrafficChart(data.trafficTrend);
            updateLastUpdatedTime();
        } catch (error) {
            console.error('Error fetching dashboard data:', error);
        }
    }

    async function fetchRealtimeData() {
        try {
            const response = await fetch(`/api/v1/dashboard/activity/${activityId}?period=7d`);
            const data = await response.json();

            updateOverviewCards(data.overview, data.previousOverview);
            updateRealtimeStatus(data.realtime);
            // Update charts every 5 seconds for real-time visualization
            renderFunnelChart(data.funnel);
            renderTrafficChart(data.trafficTrend);
            updateLastUpdatedTime();
        } catch (error) {
            console.error('Error fetching realtime data:', error);
        }
    }

    function updateOverviewCards(overview, previous) {
        // Helper to calculate percentage change
        const calcChange = (current, prev) => {
            if (!prev || prev === 0) return { value: '-', type: 'neutral' };
            const change = ((current - prev) / prev) * 100;
            return {
                value: (change > 0 ? '+' : '') + change.toFixed(1) + '%',
                type: change > 0 ? 'up' : (change < 0 ? 'down' : 'neutral')
            };
        };

        // Update Funnel KPIs
        animateValue('totalVisits', overview.totalVisits);
        updateTrend('totalVisits', calcChange(overview.totalVisits, previous?.totalVisits));

        animateValue('totalEngages', overview.totalEngages);
        updateTrend('totalEngages', calcChange(overview.totalEngages, previous?.totalEngages));

        animateValue('totalQualifies', overview.totalQualifies);
        updateTrend('totalQualifies', calcChange(overview.totalQualifies, previous?.totalQualifies));

        animateValue('purchaseCount', overview.purchaseCount);
        updateTrend('purchaseCount', calcChange(overview.purchaseCount, previous?.purchaseCount));

        // Update Marketing KPIs
        updateCurrencyValue('gmv', overview.gmv);
        updateTrend('gmv', calcChange(overview.gmv, previous?.gmv));

        updatePercentageValue('engagementRate', overview.engagementRate);
        updateTrend('engagementRate', calcChange(overview.engagementRate, previous?.engagementRate));

        updatePercentageValue('conversionRate', overview.conversionRate);
        updateTrend('conversionRate', calcChange(overview.conversionRate, previous?.conversionRate));

        updateCurrencyValue('aov', overview.averageOrderValue);
        updateTrend('aov', calcChange(overview.averageOrderValue, previous?.averageOrderValue));

        updatePercentageValue('roas', overview.roas);
        updateTrend('roas', calcChange(overview.roas, previous?.roas));
    }

    function updateCurrencyValue(id, value) {
        const el = document.getElementById(id);
        if (!el) return;
        const formatted = '₩' + Math.round(value || 0).toLocaleString();
        el.textContent = formatted;
    }

    function updatePercentageValue(id, value) {
        const el = document.getElementById(id);
        if (!el) return;
        const formatted = (value || 0).toFixed(1) + '%';
        el.textContent = formatted;
    }

    function updateTrend(id, change) {
        const el = document.getElementById(id + '-trend');
        if (el) {
            el.textContent = change.value;
            el.className = `kpi-trend ${change.type}`;
            // Update icon if needed
        }
    }

    function updateRealtimeStatus(realtime) {
        if (!realtime || !realtime.activity) return;

        const participants = realtime.activity.participantCount || 0;
        const remaining = realtime.activity.remainingStock || 0;
        const total = realtime.activity.totalStock || 100; // Fallback to 100 to avoid div by zero
        
        // Stock Percentage
        const stockPercentage = Math.round((remaining / total) * 100);
        
        // Participants Percentage
        const participantsPercentage = Math.round((participants / total) * 100);

        document.getElementById('realtimeParticipants').textContent = participants.toLocaleString();

        const stockEl = document.getElementById('realtimeStock');
        stockEl.textContent = `${remaining.toLocaleString()} (${stockPercentage}%)`;

        // Update Stock Bar
        const stockProgressBar = document.getElementById('stockProgressBar');
        if (stockProgressBar) {
            stockProgressBar.style.width = `${stockPercentage}%`;
        }

        // Update Participants Bar
        const participantsProgressBar = document.getElementById('participantsProgressBar');
        if (participantsProgressBar) {
            participantsProgressBar.style.width = `${participantsPercentage}%`;
        }

        // Change color based on stock level
        if (stockPercentage < 10) {
            stockEl.className = 'text-lg font-bold text-red-600';
            stockProgressBar.className = 'bg-red-600 h-2.5 rounded-full';
        } else if (stockPercentage < 50) {
            stockEl.className = 'text-lg font-bold text-yellow-600';
            stockProgressBar.className = 'bg-yellow-500 h-2.5 rounded-full';
        } else {
            stockEl.className = 'text-lg font-bold text-green-600';
            stockProgressBar.className = 'bg-green-600 h-2.5 rounded-full';
        }
    }

    function animateValue(id, end) {
        const obj = document.getElementById(id);
        if (!obj) return;

        const start = parseInt(obj.textContent.replace(/,/g, '')) || 0;
        if (start === end) return;

        const duration = 1000;
        const range = end - start;
        let startTime = null;

        function step(timestamp) {
            if (!startTime) startTime = timestamp;
            const progress = Math.min((timestamp - startTime) / duration, 1);
            const value = Math.floor(progress * range + start);
            obj.textContent = value.toLocaleString();
            if (progress < 1) {
                window.requestAnimationFrame(step);
            }
        }
        window.requestAnimationFrame(step);
    }

    function renderFunnelChart(funnelData) {
        const ctx = document.getElementById('funnelChart').getContext('2d');

        const labels = funnelData.map(step => step.step); // Enum name
        const data = funnelData.map(step => step.count);

        if (funnelChart) {
            // Update existing chart data smoothly
            funnelChart.data.labels = labels;
            funnelChart.data.datasets[0].data = data;
            funnelChart.update('active'); // Smooth animation
        } else {
            // Create new chart on first load
            funnelChart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Users',
                        data: data,
                        backgroundColor: [
                            'rgba(59, 130, 246, 0.8)', // Blue
                            'rgba(168, 85, 247, 0.8)', // Purple
                            'rgba(34, 197, 94, 0.8)',  // Green
                            'rgba(249, 115, 22, 0.8)'  // Orange
                        ],
                        borderRadius: 4,
                        barPercentage: 0.6
                    }]
                },
                options: {
                    indexAxis: 'y',
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            callbacks: {
                                label: function (context) {
                                    const value = context.raw;
                                    const total = data[0]; // Visit count as base
                                    const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : 0;
                                    return `${value.toLocaleString()} (${percentage}%)`;
                                }
                            }
                        }
                    },
                    scales: {
                        x: { grid: { display: false } },
                        y: { grid: { display: false } }
                    }
                }
            });
        }
    }

    function renderTrafficChart(trafficData) {
        const ctx = document.getElementById('trafficChart').getContext('2d');

        // Map data to labels (Time) and values (Count)
        // trafficData is List<TimeSeriesData> { timestamp, count }
        const labels = trafficData.map(d => {
            const date = new Date(d.timestamp);
            return date.getHours() + ':00';
        });
        const data = trafficData.map(d => d.count);

        if (trafficChart) {
            // Update existing chart data smoothly
            trafficChart.data.labels = labels;
            trafficChart.data.datasets[0].data = data;
            trafficChart.update('active'); // Smooth animation
        } else {
            // Create new chart on first load
            trafficChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Visitors',
                        data: data,
                        borderColor: 'rgb(99, 102, 241)', // Indigo
                        backgroundColor: 'rgba(99, 102, 241, 0.1)',
                        tension: 0.4,
                        fill: true,
                        pointRadius: 3,
                        pointHoverRadius: 6
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: {
                        mode: 'index',
                        intersect: false,
                    },
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            mode: 'index',
                            intersect: false
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            grid: { borderDash: [2, 2] }
                        },
                        x: {
                            grid: { display: false }
                        }
                    }
                }
            });
        }
    }

    function updateLastUpdatedTime() {
        const now = new Date();
        const timeString = now.toLocaleTimeString();
        const el = document.getElementById('lastUpdated');
        if (el) el.textContent = `Last updated: ${timeString}`;
    }
});
