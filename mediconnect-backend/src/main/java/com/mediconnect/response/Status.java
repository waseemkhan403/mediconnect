package com.mediconnect.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Status {

    private int code;
    private String message;
}
