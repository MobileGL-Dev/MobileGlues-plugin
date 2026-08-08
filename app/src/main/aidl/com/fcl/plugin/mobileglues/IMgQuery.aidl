package com.fcl.plugin.mobileglues;

/**
 * 渲染器查询：在一次性的 :mgquery 进程里执行。
 *
 * 每个方法都是阻塞的同步调用（runBench 可达一分多钟），binder 会占住调用方的一条线程，
 * 所以只能从后台线程发起。angleDirectory 传空串表示「不借 ANGLE」——AIDL 的 String
 * 不好表达 null，而渲染器那边空串与 null 本就同义（MG_ANGLE_DIR 的空值状态）。
 */
interface IMgQuery {
    String runBench(String mgDirectory, String angleDirectory);
    int benchProgress();
    String glInfo(String mgDirectory, String angleDirectory);
}
