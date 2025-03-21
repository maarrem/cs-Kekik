version = 0

cloudstream {
    authors = listOf("kraptor")
    language = "tr"
    description = "Türkçe Altyazılı Anime İzle."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/

    status = 1 // Will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://anizm.net/upload/assets/favicon.ico"
}