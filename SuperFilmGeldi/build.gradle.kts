version = 7

cloudstream {
    authors     = listOf("keyiflerolsun")
    language    = "tr"
    description = "Hd film izliyerek arkadaşlarınızla ve sevdiklerinizle iyi bir vakit geçirmek istiyorsanız açın bir film eğlenmeye bakın. Bilim kurgu filmleri, aşk drama vahşet aşk romantik sıradışı korku filmlerini izle."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.superfilmgeldi.info&sz=%size%"
}