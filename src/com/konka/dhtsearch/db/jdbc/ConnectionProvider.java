package com.konka.dhtsearch.db.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.konka.dhtsearch.db.exception.DhtException;

/**
 * @author 耳东 (cgp@0731life.com)
 *
 */
public class ConnectionProvider {

	private String driver = "com.mysql.jdbc.Driver";
	private String dbName = "dht";
	private String passwrod = "cgp888";
	private String userName = "root";
	private String url = "jdbc:mysql://localhost:3306/" + dbName;

	// -----------------------------------------------------------------
	private static ConnectionProvider instance;
	private Connection connection = null;

	private ConnectionProvider() throws DhtException {
		try {
			Class.forName(driver);
			connection = DriverManager.getConnection(url, userName, passwrod);
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static ConnectionProvider getInstance() throws DhtException {
		if (instance == null) {
			instance = new ConnectionProvider();
		}
		return instance;
	}

	public Connection getConnection() throws SQLException {
		if (connection == null || connection.isClosed()) {// 不要关闭数据库连接
			try {
				connection = DriverManager.getConnection(url, userName, passwrod);
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
		return connection;
	}

	public void closeConnection() throws DhtException {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException sqlException) {
				throw new DhtException(sqlException);
			}
		}
	}

	public void rollback() throws DhtException {
		if (connection != null) {
			try {
				connection.rollback();
			} catch (SQLException sqlException) {
				throw new DhtException(sqlException);
			}
		}
	}

}