document.addEventListener("DOMContentLoaded", () => {
    const submit = document.getElementById("submit");
    // TO-DO: 관리자용 토큰 생성 후 적용

    const token = common.getCookie("accessToken");

    if (!token) {
        alert("관리자 토큰이 필요합니다. 다시 로그인하세요.");
        window.location.href = "/";
        return;
    }
    const campaignSelect = document.getElementById('campaignId');
    function loadCampaign() {
        fetch("/api/v1/campaign")
            .then(res => res.json())
            .then(campaigns => {
                campaigns.forEach(campaign => {
                    const opt = document.createElement('option');
                    opt.value=campaign.id;
                    opt.textContent = campaign.name;
                    campaignSelect.appendChild(opt);
                });
            })
            .catch(error => {
                const def = document.getElementById("cam_default");
                def.textContent = "캠페인을 선택하지 못합니다.";
                console.log(error);
            })
    }
    loadCampaign();

    const selectableTypes = document.querySelectorAll(".campaign-type.selectable");
    const activityTypeInput = document.getElementById("activityTypeInput");

    selectableTypes.forEach((btn) => {
        btn.addEventListener("click", () => {
            selectableTypes.forEach((b) => b.classList.remove("selected"));
            btn.classList.add("selected");
            activityTypeInput.value = btn.dataset.type;
        });
    });

    submit.addEventListener("click", async () => {
        const campaignId = document.getElementById("campaignId").value;
        const activityType = activityTypeInput.value;
        const activityName = document.getElementById("activityName").value.trim();
        const limitCount = document.getElementById("limitCountInput").value;
        const startDate = document.getElementById("startDate").value;
        const endDate = document.getElementById("endDate").value;
        const status = "DRAFT"

        if (!campaignId || !activityName || !status || !startDate || !endDate || !activityType) {
            alert("필수 항목을 반드시 선택하세요");
            return;
        }
        if(limitCount <=0) {alert("최소 참여 인원은 1명입니다."); return;}

        const now = new Date();
        const start = new Date(startDate);
        const end = new Date(endDate);

        if (start <= now) {
            alert("시작 시간은 현재 시간보다 미래여야 합니다.");
            return;
        }

        if (end <= now) {
            alert("종료 시간은 현재 시간보다 미래여야 합니다.");
            return;
        }

        if (end <= start) {
            alert("종료 시간은 시작 시간보다 미래여야 합니다.");
            return;
        }

        const payload = {
            name: activityName,
            limitCount: limitCount ? Number(limitCount) : 0,
            status,
            startDate,
            endDate,
            activityType
        };

        try {
            const res = await fetch(`/api/v1/campaign/${campaignId}/activities`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}` // 관리자 토큰 교체
                },
                body: JSON.stringify(payload)
            });
            if(!res.ok) {
                alert("생성에 실패하였습니다.");
                const error = await res.json();
                throw new Error(error.message || "캠페인 활동 생성 실패");
            }
            const data = await res.json();
            alert(data.name + " 캠페인 활동이 생성되었습니다.");
            window.location.href="../admin_board.html"
        } catch (err) {
            alert("알 수 없는 오류가 발생했습니다. 나중에 다시 시도해주세요.");
            console.log(err);

        }
    })
})



