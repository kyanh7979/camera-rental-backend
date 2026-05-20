package com.camerarental.repository;

import com.camerarental.entity.RentalOrder;
import com.camerarental.entity.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RentalOrderRepository extends JpaRepository<RentalOrder, Long> {

    Optional<RentalOrder> findByOrderCode(String orderCode);

    @Query("SELECT ro FROM RentalOrder ro LEFT JOIN FETCH ro.user WHERE ro.orderCode = :orderCode")
    Optional<RentalOrder> findByOrderCodeWithUser(@Param("orderCode") String orderCode);

    // Optimized query to avoid N+1 problem - fetch orders with items and cameras in one query
    @Query("SELECT DISTINCT ro FROM RentalOrder ro LEFT JOIN FETCH ro.items oi LEFT JOIN FETCH oi.camera WHERE ro.user.id = :userId")
    List<RentalOrder> findByUserIdWithItemsAndCameras(@Param("userId") Long userId);

    @Query(value = "SELECT ro FROM RentalOrder ro LEFT JOIN FETCH ro.items oi LEFT JOIN FETCH oi.camera WHERE ro.user.id = :userId",
           countQuery = "SELECT COUNT(ro) FROM RentalOrder ro WHERE ro.user.id = :userId")
    Page<RentalOrder> findByUserIdWithItemsAndCameras(@Param("userId") Long userId, Pageable pageable);

    Page<RentalOrder> findByUserId(Long userId, Pageable pageable);

    Page<RentalOrder> findByStatus(OrderStatus status, Pageable pageable);

    List<RentalOrder> findByStatusIn(List<OrderStatus> statuses);

    List<RentalOrder> findByStatusAndEndDateBefore(OrderStatus status, LocalDate date);

    List<RentalOrder> findByStatusInAndEndDateBefore(List<OrderStatus> statuses, LocalDate date);

    long countByStatus(OrderStatus status);

    long countByStatusIn(List<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM RentalOrder o WHERE o.status IN :statuses")
    BigDecimal sumTotalAmountByStatuses(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM RentalOrder o WHERE o.status IN :statuses AND o.createdAt >= :since")
    BigDecimal sumRevenueAfter(@Param("statuses") List<OrderStatus> statuses, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT o.id) FROM RentalOrder o JOIN RentalOrderItem oi ON o.id = oi.order.id WHERE oi.camera.id = :cameraId AND o.status IN :statuses")
    long countActiveOrdersByCameraId(@Param("cameraId") Long cameraId, @Param("statuses") List<OrderStatus> statuses);

    List<RentalOrder> findTop5ByOrderByCreatedAtDesc();

    List<RentalOrder> findTop10ByStatusOrderByCreatedAtDesc(OrderStatus status);

    // ================================================
    // OVERDUE ORDER QUERIES WITH FETCH JOIN
    // ================================================

    /**
     * Find overdue orders with user eagerly loaded (avoids lazy loading issues)
     */
    @Query("""
            SELECT DISTINCT ro FROM RentalOrder ro
            LEFT JOIN FETCH ro.user
            WHERE ro.status IN :statuses
            AND ro.endDate < :now
            """)
    List<RentalOrder> findOverdueOrdersWithUser(@Param("statuses") List<OrderStatus> statuses, @Param("now") LocalDate now);

    /**
     * Find overdue orders with user AND items+cameras eagerly loaded
     */
    @Query("""
            SELECT DISTINCT ro FROM RentalOrder ro
            LEFT JOIN FETCH ro.user
            LEFT JOIN FETCH ro.items ri
            LEFT JOIN FETCH ri.camera
            WHERE ro.status IN :statuses
            AND ro.endDate < :now
            """)
    List<RentalOrder> findOverdueOrdersWithDetails(@Param("statuses") List<OrderStatus> statuses, @Param("now") LocalDate now);

    /**
     * Find orders by status with user eagerly loaded
     */
    @Query("""
            SELECT DISTINCT ro FROM RentalOrder ro
            LEFT JOIN FETCH ro.user
            WHERE ro.status IN :statuses
            ORDER BY ro.createdAt DESC
            """)
    List<RentalOrder> findByStatusInWithUser(@Param("statuses") List<OrderStatus> statuses);

    /**
     * Optimized: Get all order stats in 1 query
     * Returns: [totalOrders, pendingOrders, activeRentals, completedOrders, totalRevenue]
     */
    @Query(value = """
            SELECT
                COUNT(o.id) as totalOrders,
                SUM(CASE WHEN o.status = 'PENDING' THEN 1 ELSE 0 END) as pendingOrders,
                SUM(CASE WHEN o.status = 'RENTING' THEN 1 ELSE 0 END) as activeRentals,
                SUM(CASE WHEN o.status IN ('PAID', 'RENTING', 'RETURNED', 'COMPLETED') THEN 1 ELSE 0 END) as completedOrders,
                COALESCE(SUM(CASE WHEN o.status IN ('PAID', 'RENTING', 'RETURNED', 'COMPLETED') THEN o.total_amount ELSE 0 END), 0) as totalRevenue
            FROM rental_orders o
            """, nativeQuery = true)
    Object[] getOrderStatsNative();

    // ================================================
    // TELEGRAM BOT QUERIES - WITH FETCH JOINS + LIMIT
    // ================================================

    /**
     * Telegram: Get 5 newest orders with user AND items+cameras
     * FULLY FETCHED to avoid LazyInitializationException
     */
    @Query("""
            SELECT DISTINCT ro FROM RentalOrder ro
            LEFT JOIN FETCH ro.user
            LEFT JOIN FETCH ro.items ri
            LEFT JOIN FETCH ri.camera
            ORDER BY ro.createdAt DESC
            """)
    List<RentalOrder> findTop5OrdersWithDetailsForTelegram();

    /**
     * Telegram: Get 5 pending orders with user AND items+cameras
     * FULLY FETCHED to avoid LazyInitializationException
     */
    @Query("""
            SELECT DISTINCT ro FROM RentalOrder ro
            LEFT JOIN FETCH ro.user
            LEFT JOIN FETCH ro.items ri
            LEFT JOIN FETCH ri.camera
            WHERE ro.status = :status
            ORDER BY ro.createdAt DESC
            """)
    List<RentalOrder> findTop5PendingOrdersForTelegram(@Param("status") OrderStatus status);

    /**
     * Telegram: Get 5 orders by status with user AND items+cameras
     * FULLY FETCHED to avoid LazyInitializationException
     * Sorted by endDate ASC (soonest to return first)
     */
    @Query("""
            SELECT DISTINCT ro FROM RentalOrder ro
            LEFT JOIN FETCH ro.user
            LEFT JOIN FETCH ro.items ri
            LEFT JOIN FETCH ri.camera
            WHERE ro.status = :status
            ORDER BY ro.endDate ASC
            """)
    List<RentalOrder> findTop5OrdersByStatusForTelegram(@Param("status") OrderStatus status);

    /**
     * Telegram: Get 5 overdue orders with user AND items+cameras
     * FULLY FETCHED to avoid LazyInitializationException
     */
    @Query("""
            SELECT DISTINCT ro FROM RentalOrder ro
            LEFT JOIN FETCH ro.user
            LEFT JOIN FETCH ro.items ri
            LEFT JOIN FETCH ri.camera
            WHERE ro.status IN :statuses
            AND ro.endDate < :now
            ORDER BY ro.endDate ASC
            """)
    List<RentalOrder> findTop5OverdueOrdersForTelegram(@Param("statuses") List<OrderStatus> statuses, @Param("now") LocalDate now);
}
