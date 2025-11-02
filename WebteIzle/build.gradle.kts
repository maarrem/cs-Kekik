version = 15

cloudstream {
    authors     = listOf("keyiflerolsun")
    language    = "tr"
    description = "Her türlü filmi ister dublaj ister altyazılı, en kaliteli bir şekilde izleyebileceğiniz arşivi en geniş gerçek film izleme siteniz."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=webteizle2.com&sz=%size%"
}
