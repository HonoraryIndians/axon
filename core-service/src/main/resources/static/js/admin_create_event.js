document.addEventListener("DOMContentLoaded", () => {
    const submit = document.getElementById("submit");
    /*
    TO-DO: 관리자용 토큰 생성 후 적용
    const token = localStorage.getItem("accessToken");

    if (!token) {
        alert("관리자 토큰이 필요합니다. 다시 로그인하세요.");
        window.location.href = "/"; // 또는 로그인 페이지
        return;
    }
    */
})

document.addEventListener("click", async () => {
    const campaignId = "FIRST_COME_FIRST_SERVE"
    const eventName = document.getElementById("eventName").value.trim();
    const limitCount = document.getElementById("limitCountInput").value;
    const status = "DRAFT"

    if (!campaignId || !eventName || !status) {
        alert("캠페인 ID, 이벤트 이름, 상태는 필수입니다.");
        return;
    }

    const payload = {
        name,
        limitCount: limitCount ? Number(limitCountValue) : null,
        status
    };

})