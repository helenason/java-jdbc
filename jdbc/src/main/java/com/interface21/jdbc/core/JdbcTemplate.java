package com.interface21.jdbc.core;

import com.interface21.dao.DataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(JdbcTemplate.class);

    private final DataSource dataSource;

    public JdbcTemplate(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void update(String sql, Object... parameters) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = getPreparedStatement(conn, sql, parameters)) {
            log.debug("query : {}", sql);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new DataAccessException(e);
        }
    }

    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... parameters) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = getPreparedStatement(conn, sql, parameters);
             ResultSet rs = pstmt.executeQuery()) {
            log.debug("query : {}", sql);
            return getInstance(rowMapper, rs);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new DataAccessException(e);
        }
    }

    private <T> T getInstance(RowMapper<T> rowMapper, ResultSet rs) throws SQLException {
        if (!rs.next()) {
            return null;
        }
        return rowMapper.mapRow(rs);
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... parameters) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = getPreparedStatement(conn, sql, parameters);
             ResultSet rs = pstmt.executeQuery()) {
            log.debug("query : {}", sql);
            return getInstances(rowMapper, rs);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new DataAccessException(e);
        }
    }

    private <T> List<T> getInstances(RowMapper<T> rowMapper, ResultSet rs) throws SQLException {
        List<T> instances = new ArrayList<>();
        while (rs.next()) {
            instances.add(rowMapper.mapRow(rs));
        }
        return instances;
    }

    private PreparedStatement getPreparedStatement(Connection conn, String sql, Object[] parameters)
            throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(sql);
        setParameters(pstmt, parameters);
        return pstmt;
    }

    private void setParameters(PreparedStatement pstmt, Object[] parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            pstmt.setObject(i + 1, parameters[i]);
        }
    }
}
