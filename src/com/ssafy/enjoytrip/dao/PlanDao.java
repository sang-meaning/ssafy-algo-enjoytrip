package com.ssafy.enjoytrip.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ssafy.enjoytrip.model.Plan;
import com.ssafy.enjoytrip.util.DBUtil;

public class PlanDao {

    /** plan 과 plan_item 을 한 트랜잭션으로 저장하고 새 plan_id 를 돌려준다 */
    public int insert(Plan plan) throws SQLException {
        String planSql = "INSERT INTO plan (title, route_mode, return_to_start, total_distance_m) VALUES (?,?,?,?)";
        String itemSql = "INSERT INTO plan_item (plan_id, seq, content_id) VALUES (?,?,?)";
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int planId;
                try (PreparedStatement ps = conn.prepareStatement(planSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, plan.title());
                    ps.setString(2, plan.routeMode());
                    ps.setBoolean(3, plan.returnToStart());
                    ps.setDouble(4, plan.totalDistanceM());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        planId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                    int seq = 1;
                    for (int contentId : plan.contentIds()) {
                        ps.setInt(1, planId);
                        ps.setInt(2, seq++);
                        ps.setInt(3, contentId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                return planId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** 목록. 최신순 */
    public List<Plan> findAll() throws SQLException {
        String sql = "SELECT p.plan_id, p.title, p.route_mode, p.return_to_start, p.total_distance_m, p.created_at, "
                + "i.content_id FROM plan p LEFT JOIN plan_item i ON p.plan_id = i.plan_id "
                + "ORDER BY p.plan_id DESC, i.seq";
        Map<Integer, Plan> plans = new LinkedHashMap<>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("plan_id");
                Plan p = plans.get(id);
                if (p == null) {
                    p = map(rs, new ArrayList<>());
                    plans.put(id, p);
                }
                int contentId = rs.getInt("content_id");
                if (!rs.wasNull()) {
                    p.contentIds().add(contentId);
                }
            }
        }
        return new ArrayList<>(plans.values());
    }

    public Plan findById(int planId) throws SQLException {
        String sql = "SELECT plan_id, title, route_mode, return_to_start, total_distance_m, created_at FROM plan WHERE plan_id = ?";
        String itemSql = "SELECT content_id FROM plan_item WHERE plan_id = ? ORDER BY seq";
        try (Connection conn = DBUtil.getConnection()) {
            List<Integer> ids = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
                ps.setInt(1, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getInt(1));
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? map(rs, ids) : null;
                }
            }
        }
    }

    public boolean delete(int planId) throws SQLException {
        // plan_item 은 FK ON DELETE CASCADE 로 같이 지워진다
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM plan WHERE plan_id = ?")) {
            ps.setInt(1, planId);
            return ps.executeUpdate() > 0;
        }
    }

    private Plan map(ResultSet rs, List<Integer> contentIds) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new Plan(
                rs.getInt("plan_id"),
                rs.getString("title"),
                rs.getString("route_mode"),
                rs.getBoolean("return_to_start"),
                rs.getDouble("total_distance_m"),
                created == null ? null : created.toLocalDateTime().withNano(0).toString().replace('T', ' '),
                contentIds);
    }
}
