package com.ssafy.enjoytrip.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.ssafy.enjoytrip.model.Region;
import com.ssafy.enjoytrip.util.DBUtil;

public class RegionDao {

    private static final String UPSERT_SQL =
            "INSERT INTO ldong_region (regn_cd, regn_nm, signgu_cd, signgu_nm) VALUES (?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE regn_nm=VALUES(regn_nm), signgu_nm=VALUES(signgu_nm)";

    public int upsertAll(List<Region> list) throws SQLException {
        if (list.isEmpty()) {
            return 0;
        }
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            conn.setAutoCommit(false);
            for (Region r : list) {
                ps.setString(1, r.regnCd());
                ps.setString(2, r.regnNm());
                ps.setString(3, r.signguCd());
                ps.setString(4, r.signguNm());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            return list.size();
        }
    }

    public List<Region> findAll() throws SQLException {
        List<Region> list = new ArrayList<>();
        String sql = "SELECT regn_cd, regn_nm, signgu_cd, signgu_nm FROM ldong_region ORDER BY regn_cd, signgu_cd";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Region(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        return list;
    }
}
