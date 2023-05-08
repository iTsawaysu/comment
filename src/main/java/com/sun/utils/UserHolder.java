package com.sun.utils;

import com.sun.dto.UserDTO;

/**
 * @author sun
 */
public class UserHolder {
    private static final ThreadLocal<UserDTO> threadLocal = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        threadLocal.set(user);
    }

    public static UserDTO getUser(){
        return threadLocal.get();
    }

    public static void removeUser(){
        threadLocal.remove();
    }
}
