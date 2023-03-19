package org.example.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import java.util.List;

public abstract class PermissionChecker {
    public static boolean userHasPermission(MessageReceivedEvent event, Permission perm){
        // Iterate through roles and get all roles with the specified permission.
        if(event.getMember() != null) {
            List<Role> userRoles = event.getMember().getRoles();
            for (Role role : userRoles) {
                if (role.hasPermission(perm)) {
                    return true;
                }
            }
        }
        return false;
    }
}
