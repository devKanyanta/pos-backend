package com.devkanyanta.pos.customer;

import java.util.List;

import com.devkanyanta.pos.customer.dto.CreateCustomerRequest;
import com.devkanyanta.pos.customer.dto.CustomerResponse;
import com.devkanyanta.pos.customer.dto.UpdateCustomerRequest;

public interface CustomerService {

    CustomerResponse createCustomer(CreateCustomerRequest request);

    List<CustomerResponse> getAllCustomers();

    CustomerResponse getCustomerById(Long id);

    CustomerResponse updateCustomer(Long id, UpdateCustomerRequest request);

    void deleteCustomer(Long id);

    CustomerResponse searchByPhone(String phone);
}
