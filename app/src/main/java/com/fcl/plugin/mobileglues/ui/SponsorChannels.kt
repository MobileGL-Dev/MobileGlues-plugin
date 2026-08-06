package com.fcl.plugin.mobileglues.ui

/**
 * 一个赞助渠道。
 *
 * [label] 是平台加收款方，[url] 原样展示给用户看——爱发电有 afdian.com / afdian.net /
 * ifdian.net 三个域名在用，收款方也各不相同，只写「爱发电」用户没法知道钱去了哪儿。
 */
data class SponsorChannel(val label: String, val url: String)

/**
 * 全部赞助渠道。
 *
 * 项目账号排在最前，然后按 [com.fcl.plugin.mobileglues.R.string.info_author] 的顺序列出
 * 三位开发者各自的收款页——这是一份名单，不是一个排行榜，顺序就该和署名一致。
 */
val SponsorChannels = listOf(
    SponsorChannel("爱发电 · MobileGlues", "https://afdian.com/a/MobileGlues"),
    SponsorChannel("Buy Me a Coffee · Swung", "https://www.buymeacoffee.com/Swung0x48"),
    SponsorChannel("爱发电 · BZLZHH", "https://www.ifdian.net/a/bzlzhh"),
    SponsorChannel("爱发电 · Tungsten", "https://afdian.net/a/tungs"),
)
