const common = (() => {
    function getCookie(name) {
        const pattern =
            "(?:^|; )" + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, "\\$1") + "=([^;]*)";
        const match = document.cookie.match(new RegExp(pattern));
        return match ? decodeURIComponent(match[1]) : undefined;
    }

    // 필요한 함수만 외부에 노출
    return {
        getCookie,
        // 추가 기능은 여기 추가
    };
})();