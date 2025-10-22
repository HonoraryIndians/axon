document.addEventListener("DOMContentLoaded", () => {
    const submit = document.getElementById("submit");
    // TO-DO: 관리자용 토큰 생성 후 적용

    const token = common.getCookie("accessToken");

    if (!token) {
        alert("관리자 토큰이 필요합니다. 다시 로그인하세요.");
        window.location.href = "/";
        return;
    }

    const selectableTypes = document.querySelectorAll(".campaign-type.selectable");
    const campaignTypeInput = document.getElementById("campaignTypeInput");

    selectableTypes.forEach((btn) => {
        btn.addEventListener("click", () => {
            selectableTypes.forEach((b) => b.classList.remove("selected"));
            btn.classList.add("selected");
            campaignTypeInput.value = btn.dataset.type;
        });
    });

    submit.addEventListener("click", async () => {
        const campaignId = campaignTypeInput.value;
        const eventName = document.getElementById("eventName").value.trim();
        const limitCount = document.getElementById("limitCountInput").value;
        const status = "DRAFT"

        if (!campaignId || !eventName || !status) {
            alert("캠페인 ID, 이벤트 이름, 상태는 필수입니다.");
            return;
        }

        const payload = {
            name: eventName,
            limitCount: limitCount ? Number(limitCount) : 0,
            status
        };

        try {
            const res = await fetch(`/api/v1/campaign/${campaignId}/events`, {
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
                throw new Error(error.message || "이벤트 생성 실패");
            }
            const data = await res.json();
            alert(data.name + " 이벤트가 생성되었습니다.");
            window.location.href="../admin_board.html"
        } catch (err) {
            alert("알 수 없는 오류가 발생했습니다. 나중에 다시 시도해주세요.");
            console.log(err);

        }
    })
})



