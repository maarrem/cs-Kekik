version = 75

cloudstream {
    authors     = listOf("keyiflerolsun", "yusiqo", "inatchii", "JustRelaxable")
    language    = "tr"
    description = "RecTv APK, Türkiye’deki en popüler Çevrimiçi Medya Akış platformlarından biridir. Filmlerin, Canlı Sporların, Web Dizilerinin ve çok daha fazlasının keyfini ücretsiz çıkarın."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "Live", "TvSeries")
    iconUrl = "https://rectvapp.com.tr/wp-content/uploads/2024/08/cropped-cropped-Screenshot_2023-08-31_at_11.02.55_PM-removebg-preview-32x32.webp"
}