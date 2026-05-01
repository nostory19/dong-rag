package com.dong.dongrag.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author by hongdou
 * @date 2025/12/14.
 * @DESC: 删除请求参数
 */

@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
     private Long id;

     public static final long serialVersionUID = 1L;
}
