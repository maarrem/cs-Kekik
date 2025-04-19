version = 9

cloudstream {
    authors     = listOf("keyiflerolsun")
    language    = "tr"
    description = "Diziyou en kaliteli Türkçe dublaj ve altyazılı yabancı dizi izleme sitesidir. Güncel ve efsanevi dizileri 1080p Full HD kalitede izlemek için hemen tıkla!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.diziyou.co&sz=%size%"
}