document.addEventListener('DOMContentLoaded', function () {
    const activityId = document.getElementById('activityId').value;
    let currentPeriod = '7d'; // Default
    let funnelChart = null;
    let trafficChart = null;

    // Initialize Dashboard
    initDashboard();

    // Make functions available globally for HTML onclick handlers
    window.togglePeriodDropdown = function () {
        document.getElementById('periodDropdownMenu').classList.toggle('hidden');
    }

    // Close dropdown when clicking outside
    document.addEventListener('click', function (event) {
        const dropdown = document.getElementById('periodDropdownMenu');
        const btn = document.getElementById('periodDropdownBtn');
        if (!dropdown.contains(event.target) && !btn.contains(event.target)) {
            dropdown.classList.add('hidden');
        }
    });

    window.changePeriod = function (period, label) {
        currentPeriod = period;
        document.getElementById('currentPeriodLabel').textContent = label;
        togglePeriodDropdown(); // Close menu

        // Refresh data immediately
        fetchDashboardData();
    }

    function initDashboard() {
        fetchDashboardData();
        // Poll every 5 seconds for real-time updates
        setInterval(fetchRealtimeData, 5000);
    }

    async function fetchDashboardData() {
        try {
            const response = await fetch(`/api/v1/dashboard/activity/${activityId}?period=${currentPeriod}`);
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
            // Realtime data might not need period, but keeping it consistent or fetching only realtime endpoint
            // The controller for activity dashboard uses period for overview/graphs
            // For strict realtime status, period matters less, but let's keep it consistent
            const response = await fetch(`/api/v1/dashboard/activity/${activityId}?period=${currentPeriod}`);
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
            funnelChart.data.labels = labels;
            funnelChart.data.datasets[0].data = data;
            funnelChart.update('active');
        } else {
            funnelChart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Users',
                        data: data,
                        // Professional Blue Gradient equivalent steps or distinct slate/blue shades
                        backgroundColor: [
                            '#2563eb', // Blue 600
                            '#3b82f6', // Blue 500
                            '#60a5fa', // Blue 400
                            '#93c5fd'  // Blue 300
                        ],
                        borderRadius: 3,
                        barPercentage: 0.5
                    }]
                },
                options: {
                    indexAxis: 'y',
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            backgroundColor: '#1e293b',
                            padding: 10,
                            cornerRadius: 4,
                            callbacks: {
                                label: function (context) {
                                    const value = context.raw;
                                    const total = data[0];
                                    const percentage = total > 0 ? ((value / total) * 100).toFixed(1) : 0;
                                    return `${value.toLocaleString()} (${percentage}%)`;
                                }
                            }
                        }
                    },
                    scales: {
                        x: {
                            grid: { display: false, drawBorder: false },
                            ticks: { display: false }
                        },
                        y: {
                            grid: { display: false, drawBorder: false },
                            ticks: {
                                font: { size: 12, family: "-apple-system, BlinkMacSystemFont, 'Segoe UI'" },
                                color: '#64748b'
                            }
                        }
                    }
                }
            });
        }
    }

    function renderTrafficChart(trafficData) {
        const ctx = document.getElementById('trafficChart').getContext('2d');

        const labels = trafficData.map(d => {
            const date = new Date(d.timestamp);
            return date.getHours() + ':00';
        });
        const data = trafficData.map(d => d.count);

        if (trafficChart) {
            trafficChart.data.labels = labels;
            trafficChart.data.datasets[0].data = data;
            trafficChart.update('active');
        } else {
            trafficChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Visitors',
                        data: data,
                        borderColor: '#2563eb', // Blue 600
                        backgroundColor: 'rgba(37, 99, 235, 0.05)', // Very subtle fill
                        borderWidth: 2,
                        tension: 0.3,
                        fill: true,
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        pointHoverBackgroundColor: '#2563eb',
                        pointHoverBorderColor: '#fff',
                        pointHoverBorderWidth: 2
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
                            backgroundColor: '#1e293b',
                            titleColor: '#f8fafc',
                            bodyColor: '#f8fafc',
                            padding: 10,
                            cornerRadius: 4,
                            displayColors: false,
                            intersect: false
                        }
                    },
                    scales: {
                        y: {
                            beginAtZero: true,
                            grid: {
                                borderDash: [4, 4],
                                color: '#e2e8f0',
                                drawBorder: false
                            },
                            ticks: {
                                maxTicksLimit: 5,
                                color: '#94a3b8',
                                font: { size: 11 }
                            }
                        },
                        x: {
                            grid: { display: false, drawBorder: false },
                            ticks: {
                                maxTicksLimit: 8,
                                color: '#94a3b8',
                                font: { size: 11 }
                            }
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
