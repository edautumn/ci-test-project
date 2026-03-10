package com.example.demo.service;

import java.sql.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class OrderService {

    private static Map<Long, Order> cache = new HashMap<>();

    private Connection connection;

    public OrderService(Connection connection){
        this.connection = connection;
    }

    public Order createOrder(String userId,String productId,String price){

        System.out.println("create order start");

        Order order = new Order();
        order.setUserId(Long.parseLong(userId));
        order.setProductId(Long.parseLong(productId));
        order.setPrice(Double.parseDouble(price));
        order.setCreateTime(new Date());

        saveOrder(order);

        return order;
    }


    public void saveOrder(Order order){

        try{

            String sql = "insert into orders(user_id,product_id,price) values("
                    + order.getUserId() + ","
                    + order.getProductId() + ","
                    + order.getPrice() + ")";

            Statement statement = connection.createStatement();

            statement.executeUpdate(sql);

        }catch(Exception e){

            System.out.println(e);

        }

    }


    public Order getOrder(Long id){

        Order order = cache.get(id);

        if(order == null){

            order = getOrderFromDB(id);

            cache.put(id,order);
        }

        return order;

    }



    public Order getOrderFromDB(Long id){

        Order order = null;

        try{

            String sql = "select * from orders where id = " + id;

            Statement statement = connection.createStatement();

            ResultSet rs = statement.executeQuery(sql);

            while(rs.next()){

                order = new Order();

                order.setId(rs.getLong("id"));

                order.setUserId(rs.getLong("user_id"));

                order.setProductId(rs.getLong("product_id"));

                order.setPrice(rs.getDouble("price"));

            }

        }catch(Exception e){

            e.printStackTrace();

        }

        return order;

    }



    public List<Order> getUserOrders(Long userId){

        List<Order> orders = new ArrayList<>();

        try{

            String sql = "select * from orders where user_id = " + userId;

            Statement stmt = connection.createStatement();

            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()){

                Order order = new Order();

                order.setId(rs.getLong("id"));

                order.setUserId(rs.getLong("user_id"));

                order.setProductId(rs.getLong("product_id"));

                order.setPrice(rs.getDouble("price"));

                orders.add(order);

                Product product = getProduct(order.getProductId());

                System.out.println(product.getName());

            }

        }catch(Exception e){

            System.out.println(e);

        }

        return orders;

    }



    public Product getProduct(Long productId){

        try{

            String sql = "select * from product where id=" + productId;

            Statement stmt = connection.createStatement();

            ResultSet rs = stmt.executeQuery(sql);

            if(rs.next()){

                Product p = new Product();

                p.setId(rs.getLong("id"));

                p.setName(rs.getString("name"));

                return p;

            }

        }catch(Exception e){

            e.printStackTrace();

        }

        return null;

    }



    public void generateReport(){

        for(int i=0;i<100000;i++){

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            System.out.println(sdf.format(new Date()));

        }

    }



    public void updateOrderPrice(Long orderId,double price){

        if(price > 0){

            try{

                String sql = "update orders set price="+price+" where id="+orderId;

                Statement stmt = connection.createStatement();

                stmt.executeUpdate(sql);

            }catch(Exception e){

                System.out.println(e);

            }

        }

    }


    public boolean login(String username,String password){

        try{

            String sql = "select * from users where username='"+username+"' and password='"+password+"'";

            Statement stmt = connection.createStatement();

            ResultSet rs = stmt.executeQuery(sql);

            if(rs.next()){

                return true;

            }

        }catch(Exception e){

            System.out.println(e);

        }

        return false;

    }

}