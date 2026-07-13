package com.devkanyanta.pos.customer;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.devkanyanta.pos.auth.security.PermissionConstants;
import com.devkanyanta.pos.customer.dto.CreateCustomerRequest;
import com.devkanyanta.pos.customer.dto.CustomerResponse;
import com.devkanyanta.pos.customer.dto.UpdateCustomerRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.CUSTOMER_CREATE + "')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
            @RequestBody CreateCustomerRequest request
    ) {
        return customerService.createCustomer(request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionConstants.CUSTOMER_VIEW + "')")
    public List<CustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.CUSTOMER_VIEW + "')")
    public CustomerResponse getCustomerById(
            @PathVariable Long id
    ) {
        return customerService.getCustomerById(id);
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('" + PermissionConstants.CUSTOMER_VIEW + "')")
    public CustomerResponse searchByPhone(
            @RequestParam("phone") String phone
    ) {
        return customerService.searchByPhone(phone);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.CUSTOMER_UPDATE + "')")
    public CustomerResponse updateCustomer(
            @PathVariable Long id,
            @RequestBody UpdateCustomerRequest request
    ) {
        return customerService.updateCustomer(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + PermissionConstants.CUSTOMER_DELETE + "')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomer(
            @PathVariable Long id
    ) {
        customerService.deleteCustomer(id);
    }
}
