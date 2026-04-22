package com.example.udtddk.models;
public class User {
    private String HoTen;
    private String Email;
    private String MatKhau;
    private String GioiTinh;
    private String NgaySinh;
    private double CanNang;
    private double ChieuCao;
    private String VaiTro;
    public User() {}
    public User(String hoTen, String email, String matKhau, String gioiTinh, String ngaySinh, double canNang,
                double chieuCao, String vaiTro) {
        HoTen = hoTen;
        Email = email;
        MatKhau = matKhau;
        GioiTinh = gioiTinh;
        NgaySinh = ngaySinh;
        CanNang = canNang;
        ChieuCao = chieuCao;
        VaiTro = vaiTro;
    }
    public String getHoTen() { return HoTen; }
    public String getEmail() { return Email; }
    public String getMatKhau() { return MatKhau; }
    public String getGioiTinh() { return GioiTinh; }
    public String getNgaySinh() { return NgaySinh; }
    public double getCanNang() { return CanNang; }
    public double getChieuCao() { return ChieuCao; }
    public String getVaiTro() { return VaiTro; }
    public void setHoTen(String hoTen) { HoTen = hoTen; }
    public void setEmail(String email) { Email = email; }
    public void setMatKhau(String matKhau) { MatKhau = matKhau; }
    public void setGioiTinh(String gioiTinh) { GioiTinh = gioiTinh; }
    public void setNgaySinh(String ngaySinh) { NgaySinh = ngaySinh; }
    public void setCanNang(double canNang) { CanNang = canNang; }
    public void setChieuCao(double chieuCao) { ChieuCao = chieuCao; }
    public void setVaiTro(String vaiTro) { VaiTro = vaiTro; }
}