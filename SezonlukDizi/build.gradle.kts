version = 7

cloudstream {
    authors     = listOf("keyiflerolsun","JustRelaxable")
    language    = "tr"
    description = "Güncel ve eski dizileri en iyi görüntü kalitesiyle bulabileceğiniz yabancı dizi izleme siteniz."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=sezonlukdizi6.com&sz=%size%"
}