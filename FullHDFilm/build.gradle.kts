version = 14

cloudstream {
    authors     = listOf("keyiflerolsun")
    language    = "tr"
    description = "Fullhdfilm ile en yeni vizyon filmler Full HD ve kesintisiz film sizlerle. Özgün film arşivimizle en üstün kaliteli film izle keyfini sunuyoruz."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=fullhdfilm.pro&sz=%size%"
}