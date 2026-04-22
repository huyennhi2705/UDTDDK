package com.example.udtddk.models;

import java.util.Date;

public class HealthRecord {

    // Đúng theo schema bạn cung cấp
    private int ChiSoId;
    private float BMI;
    private float CanNang;
    private float ChieuCao;
    private String GioNgu;
    private String GioThuc;
    private float LuongNuoc;
    private long Ngay;           // timestamp
    private String NgayTao;      // yyyy-MM-dd
    private int NguoiDungId;
    private float ThoiGianNgu;   // số giờ ngủ

    public HealthRecord() {}     // BẮT BUỘC cho Firebase

    public HealthRecord(String ngayTao, float canNang, float chieuCao, float luongNuoc,
                        float thoiGianNgu, String gioNgu, String gioThuc, float bmi) {
        this.NgayTao = ngayTao;
        this.CanNang = canNang;
        this.ChieuCao = chieuCao;
        this.LuongNuoc = luongNuoc;
        this.ThoiGianNgu = thoiGianNgu;
        this.GioNgu = gioNgu;
        this.GioThuc = gioThuc;
        this.BMI = bmi;
        this.Ngay = new Date().getTime();
    }

    // Getter & Setter
    public int getChiSoId() { return ChiSoId; }
    public void setChiSoId(int chiSoId) { this.ChiSoId = chiSoId; }

    public float getBMI() { return BMI; }
    public void setBMI(float bmi) { this.BMI = bmi; }

    public float getCanNang() { return CanNang; }
    public void setCanNang(float canNang) { this.CanNang = canNang; }

    public float getChieuCao() { return ChieuCao; }
    public void setChieuCao(float chieuCao) { this.ChieuCao = chieuCao; }

    public String getGioNgu() { return GioNgu; }
    public void setGioNgu(String gioNgu) { this.GioNgu = gioNgu; }

    public String getGioThuc() { return GioThuc; }
    public void setGioThuc(String gioThuc) { this.GioThuc = gioThuc; }

    public float getLuongNuoc() { return LuongNuoc; }
    public void setLuongNuoc(float luongNuoc) { this.LuongNuoc = luongNuoc; }

    public long getNgay() { return Ngay; }
    public void setNgay(long ngay) { this.Ngay = ngay; }

    public String getNgayTao() { return NgayTao; }
    public void setNgayTao(String ngayTao) { this.NgayTao = ngayTao; }

    public int getNguoiDungId() { return NguoiDungId; }
    public void setNguoiDungId(int nguoiDungId) { this.NguoiDungId = nguoiDungId; }

    public float getThoiGianNgu() { return ThoiGianNgu; }
    public void setThoiGianNgu(float thoiGianNgu) { this.ThoiGianNgu = thoiGianNgu; }
}