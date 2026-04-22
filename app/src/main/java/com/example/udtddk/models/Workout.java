package com.example.udtddk.models;

import com.google.firebase.database.IgnoreExtraProperties;
import com.google.firebase.database.PropertyName;

@IgnoreExtraProperties
public class Workout {

    @PropertyName("TenBaiTap")
    public String TenBaiTap;

    @PropertyName("MoTa")
    public String MoTa;

    @PropertyName("PhanLoaiTheoBMI")
    public String PhanLoaiTheoBMI;

    @PropertyName("MucDo")
    public String MucDo;

    @PropertyName("ThoiGianTap")
    public int ThoiGianTap;

    @PropertyName("LuongCaloTieuHao")
    public int LuongCaloTieuHao;

    @PropertyName("DuongDanVideo")
    public String DuongDanVideo;

    @PropertyName("MucTieuId")
    public int MucTieuId;

    public Workout() {}

    public Workout(String tenBaiTap, String moTa, String phanLoaiTheoBMI,
                   String mucDo, int thoiGianTap,
                   int luongCaloTieuHao, String duongDanVideo) {
        TenBaiTap        = tenBaiTap;
        MoTa             = moTa;
        PhanLoaiTheoBMI  = phanLoaiTheoBMI;
        MucDo            = mucDo;
        ThoiGianTap      = thoiGianTap;
        LuongCaloTieuHao = luongCaloTieuHao;
        DuongDanVideo    = duongDanVideo;
    }

    // ── Getters (dùng @PropertyName để Firebase map đúng) ──
    @PropertyName("TenBaiTap")
    public String getTenBaiTap()        { return TenBaiTap != null ? TenBaiTap : ""; }

    @PropertyName("MoTa")
    public String getMoTa()             { return MoTa != null ? MoTa : ""; }

    @PropertyName("PhanLoaiTheoBMI")
    public String getPhanLoaiTheoBMI()  { return PhanLoaiTheoBMI != null ? PhanLoaiTheoBMI : ""; }

    @PropertyName("MucDo")
    public String getMucDo()            { return MucDo != null ? MucDo : ""; }

    @PropertyName("ThoiGianTap")
    public int getThoiGianTap()         { return ThoiGianTap; }

    @PropertyName("LuongCaloTieuHao")
    public int getLuongCaloTieuHao()    { return LuongCaloTieuHao; }

    @PropertyName("DuongDanVideo")
    public String getDuongDanVideo()    { return DuongDanVideo != null ? DuongDanVideo : ""; }
}