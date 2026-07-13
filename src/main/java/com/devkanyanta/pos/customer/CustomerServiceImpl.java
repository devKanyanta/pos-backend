package com.devkanyanta.pos.customer;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.devkanyanta.pos.customer.dto.CreateCustomerRequest;
import com.devkanyanta.pos.customer.dto.CustomerResponse;
import com.devkanyanta.pos.customer.dto.UpdateCustomerRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Override
    public CustomerResponse createCustomer(CreateCustomerRequest request) {

        validateCreateRequest(request);

        if (StringUtils.hasText(request.getPhone())
                && customerRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Customer phone number already exists");
        }

        if (StringUtils.hasText(request.getEmail())
                && customerRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Customer email already exists");
        }

        CustomerEntity customer = CustomerEntity.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .tpin(request.getTpin())
                .address(request.getAddress())
                .build();

        return toResponse(customerRepository.save(customer));
    }

    @Override
    public List<CustomerResponse> getAllCustomers() {

        return customerRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public CustomerResponse getCustomerById(Long id) {

        return toResponse(findCustomerById(id));
    }

    @Override
    public CustomerResponse updateCustomer(Long id, UpdateCustomerRequest request) {

        CustomerEntity customer = findCustomerById(id);

        if (StringUtils.hasText(request.getFirstName())) {
            customer.setFirstName(request.getFirstName());
        }

        if (StringUtils.hasText(request.getLastName())) {
            customer.setLastName(request.getLastName());
        }

        if (request.getPhone() != null
                && !request.getPhone().equals(customer.getPhone())) {

            if (StringUtils.hasText(request.getPhone())
                    && customerRepository.existsByPhone(request.getPhone())) {
                throw new RuntimeException("Customer phone number already exists");
            }
            customer.setPhone(request.getPhone());
        }

        if (request.getEmail() != null
                && !request.getEmail().equals(customer.getEmail())) {

            if (StringUtils.hasText(request.getEmail())
                    && customerRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Customer email already exists");
            }
            customer.setEmail(request.getEmail());
        }

        if (request.getTpin() != null) {
            customer.setTpin(request.getTpin());
        }

        if (request.getAddress() != null) {
            customer.setAddress(request.getAddress());
        }

        if (request.getIsActive() != null) {
            customer.setIsActive(request.getIsActive());
        }

        return toResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponse searchByPhone(String phone) {

        CustomerEntity customer = customerRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("Customer not found with phone: " + phone));

        return toResponse(customer);
    }

    @Override
    public void deleteCustomer(Long id) {

        CustomerEntity customer = findCustomerById(id);
        customerRepository.delete(customer);
    }

    private CustomerEntity findCustomerById(Long id) {

        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
    }

    private void validateCreateRequest(CreateCustomerRequest request) {

        if (request == null) {
            throw new RuntimeException("Customer request is required");
        }

        if (!StringUtils.hasText(request.getPhone())
                && !StringUtils.hasText(request.getEmail())) {
            throw new RuntimeException("Either phone number or email is required");
        }
    }

    private CustomerResponse toResponse(CustomerEntity customer) {

        return CustomerResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .tpin(customer.getTpin())
                .address(customer.getAddress())
                .isActive(customer.getIsActive())
                .createdAt(customer.getCreatedAt())
                .build();
    }
}
