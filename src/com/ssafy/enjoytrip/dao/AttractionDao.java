package com.ssafy.enjoytrip.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.enjoytrip.model.Attraction;
import com.ssafy.enjoytrip.util.DBUtil;

public class AttractionDao {

    private static final String COLUMNS =
            "content_id, content_type_id, title, addr1, addr2, zipcode, tel, first_image, first_image2, "
            + "latitude, longitude, ldong_regn_cd, ldong_signgu_cd, lcls_systm1, lcls_systm2, lcls_systm3, modified_time";

    // 같은 content_id 가 있으면 새 값으로 덮어쓴다. 적재를 여러 번 돌려도 안전하다.
    private static final String UPSERT_SQL =
            "INSERT INTO attraction (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE content_type_id=VALUES(content_type_id), title=VALUES(title), "
            + "addr1=VALUES(addr1), addr2=VALUES(addr2), zipcode=VALUES(zipcode), tel=VALUES(tel), "
            + "first_image=VALUES(first_image), first_image2=VALUES(first_image2), "
            + "latitude=VALUES(latitude), longitude=VALUES(longitude), "
            + "ldong_regn_cd=VALUES(ldong_regn_cd), ldong_signgu_cd=VALUES(ldong_signgu_cd), "
            + "lcls_systm1=VALUES(lcls_systm1), lcls_systm2=VALUES(lcls_systm2), lcls_systm3=VALUES(lcls_systm3), "
            + "modified_time=VALUES(modified_time)";

    public int upsertAll(List<Attraction> list) throws SQLException {
        if (list.isEmpty()) {
            return 0;
        }
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            conn.setAutoCommit(false);
            for (Attraction a : list) {
                int i = 1;
                ps.setInt(i++, a.contentId());
                ps.setInt(i++, a.contentTypeId());
                ps.setString(i++, a.title());
                ps.setString(i++, a.addr1());
                ps.setString(i++, a.addr2());
                ps.setString(i++, a.zipcode());
                ps.setString(i++, a.tel());
                ps.setString(i++, a.firstImage());
                ps.setString(i++, a.firstImage2());
                ps.setDouble(i++, a.latitude());
                ps.setDouble(i++, a.longitude());
                ps.setString(i++, a.ldongRegnCd());
                ps.setString(i++, a.ldongSignguCd());
                ps.setString(i++, a.lclsSystm1());
                ps.setString(i++, a.lclsSystm2());
                ps.setString(i++, a.lclsSystm3());
                ps.setString(i++, a.modifiedTime());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            return list.size();
        }
    }

    /** 서버 시작 시 메모리 인덱스를 만들기 위해 전체를 읽는다. 약 5만 건. */
    public List<Attraction> findAll() throws SQLException {
        List<Attraction> list = new ArrayList<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + COLUMNS + " FROM attraction");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public int count() throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM attraction");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Attraction map(ResultSet rs) throws SQLException {
        return new Attraction(
                rs.getInt("content_id"),
                rs.getInt("content_type_id"),
                rs.getString("title"),
                rs.getString("addr1"),
                rs.getString("addr2"),
                rs.getString("zipcode"),
                rs.getString("tel"),
                rs.getString("first_image"),
                rs.getString("first_image2"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("ldong_regn_cd"),
                rs.getString("ldong_signgu_cd"),
                rs.getString("lcls_systm1"),
                rs.getString("lcls_systm2"),
                rs.getString("lcls_systm3"),
                rs.getString("modified_time"));
    }
}
