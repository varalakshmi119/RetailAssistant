package com.example.retailassistant

import com.example.retailassistant.ui.navigation.Screen
import org.junit.Test
import org.junit.Assert.*

/**
 * Simple test to verify navigation routes are properly constructed
 */
class NavigationTest {
    
    @Test
    fun `Screen Main createRoute should generate valid route`() {
        val routeWithSyncError = Screen.Main.createRoute(true)
        val routeWithoutSyncError = Screen.Main.createRoute(false)
        
        assertEquals("main/true", routeWithSyncError)
        assertEquals("main/false", routeWithoutSyncError)
    }
    
    @Test
    fun `Screen Auth route should be simple string`() {
        assertEquals("auth", Screen.Auth.route)
    }
    
    @Test
    fun `Screen InvoiceDetail createRoute should generate valid route`() {
        val invoiceId = "test-invoice-123"
        val route = Screen.InvoiceDetail.createRoute(invoiceId)
        
        assertEquals("invoice_detail/test-invoice-123", route)
    }
    
    @Test
    fun `Screen CustomerDetail createRoute should generate valid route`() {
        val customerId = "test-customer-456"
        val route = Screen.CustomerDetail.createRoute(customerId)
        
        assertEquals("customer_detail/test-customer-456", route)
    }
}