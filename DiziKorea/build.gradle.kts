version = 5

cloudstream {
    authors     = listOf("keyiflerolsun")
    language    = "tr"
    description = "En Güncel Kore Dizileri izleme Sitesi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("AsianDrama")
    iconUrl = "https://www.google.com/s2/favicons?domain=https://dizikorea.vip&sz=%size%"
}