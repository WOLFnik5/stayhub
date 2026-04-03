package com.bookingapp.domain.model;

import com.bookingapp.domain.model.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private UserRole role;
}
