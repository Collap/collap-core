package io.collap.std.post.post;

import io.collap.controller.TemplateController;
import io.collap.controller.communication.Request;
import io.collap.controller.communication.Response;
import io.collap.resource.TemplatePlugin;
import io.collap.std.post.entity.Category;
import io.collap.std.post.entity.Post;
import io.collap.std.user.entity.User;
import io.collap.std.markdown.MarkdownPlugin;
import io.collap.std.post.util.PostUtil;
import org.hibernate.Session;

import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * GET:
 *      Either write a new post or edit a post that already exists.
 *      The remainingPath determines whether a new post is created: When an ID is supplied, the post with that ID is edited.
 *      Otherwise, a new post is created.
 * POST:
 *      Update or add a post.
 * A post with an ID of -1 is considered a non-existent post and signals a newly created post.
 */
public class EditPost extends TemplateController {

    public EditPost (TemplatePlugin plugin) {
        super (plugin);
    }

    @Override
    public void execute (String remainingPath, Request request, Response response) throws IOException {
        HttpSession httpSession = request.getHttpRequest ().getSession ();
        if (httpSession == null || httpSession.getAttribute ("user") == null) {
            response.getWriter ().write ("You need to be logged in!");
            return;
        }

        Session session = plugin.getCollap ().getSessionFactory ().getCurrentSession ();

        if (request.getMethod () == Request.Method.get) {
            Post post = PostUtil.getPostFromDatabaseOrCreate (session, remainingPath, true);
            if (post != null) {
                User author = (User) httpSession.getAttribute ("user");
                if (post.getId () == -1 || author.getId () == post.getAuthor ().getId ()) {
                    Map<String, Object> model = new HashMap<> ();
                    model.put ("post", post);
                    model.put ("categories", post.getCategories ());
                    // TODO: The following solution is temporary.
                    String categoryString = "";
                    for (Category category : post.getCategories ()) {
                        if (!categoryString.isEmpty ()) {
                            categoryString += ",";
                        }
                        categoryString += category.getName ();
                    }
                    model.put ("categoryString", categoryString);
                    plugin.renderAndWriteTemplate ("post/Edit", model, response.getWriter ());
                }else {
                    response.getWriter ().write ("Insufficient editing permissions!");
                }
            }else {
                // TODO: Potential source of knowledge for an outsider of which IDs are taken.
                response.getWriter ().write ("Post not found!");
            }
        }else if (request.getMethod () == Request.Method.post) {
            editPost (session, request, response);
        }
    }

    private void editPost (Session session, Request request, Response response) throws IOException {
        // TODO: Possible validation.
        long id;
        try {
            id = Long.parseLong (request.getHttpRequest ().getParameter ("id"));
        } catch (NumberFormatException ex) {
            response.getWriter ().write ("Hidden 'id' input field supplied a wrong number!");
            return;
        }

        /* Note: It is assumed that a check whether a user is logged in already passed. */
        User author = (User) request.getHttpRequest ().getSession ().getAttribute ("user");

        Date now = new Date ();
        Post post;
        if (id == -1) { /* Create new post! */
            post = new Post ();
            post.setAuthor (author);
            post.setPublishingDate (now);
        }else {
            post = (Post) session.get (Post.class, id);
            if (post == null) {
                response.getWriter ().write ("Post to edit could not be found!");
                return;
            }

            /* Validate author. */
            if (!post.getAuthor ().getId ().equals (author.getId ())) {
                response.getWriter ().write ("Insufficient rights to edit the post!");
                return;
            }
        }

        post.setTitle (request.getHttpRequest ().getParameter ("title"));
        post.setContent (request.getHttpRequest ().getParameter ("content"));
        post.setLastEdit (now);

        MarkdownPlugin markdownPlugin = (MarkdownPlugin) plugin.getCollap ().getPluginManager ().getPlugins ().get ("std-markdown");
        post.setCompiledContent (markdownPlugin.convertMarkdownToHTML (post.getContent ()));

        /* Update categories. */
        updateCategories (post, request, response);

        /* Update post. */
        session.persist (post);
        response.getWriter ().write ("Post successfully created or updated!");
    }

    private void updateCategories (Post post, Request request, Response response) throws IOException {
        String[] inputNames = request.getHttpRequest ().getParameter ("categories").split (",");
        Set<Category> categories = post.getCategories ();

        /* Trim category names. */
        for (int i = 0; i < inputNames.length; ++i) {
            inputNames[i] = inputNames[i].trim ();
        }

        /* Remove unreferenced categories. */
        Iterator<Category> iterator = categories.iterator ();
        while (iterator.hasNext ()) {
            Category category = iterator.next ();
            boolean contained = false;
            for (String name : inputNames) {
                if (name.isEmpty ()) continue;
                if (category.getName ().equals (name)) {
                    contained = true;
                    break;
                }
            }

            if (!contained) {
                iterator.remove ();
            }
        }

        /* Find all categories that are not referenced yet to minimize database load. */
        List<String> newNames = new ArrayList<> ();
        for (String name : inputNames) {
            if (name.isEmpty ()) continue;

            boolean contained = false;
            for (Category category : categories) {
                if (category.getName ().equals (name)) {
                    contained = true;
                    break;
                }
            }

            if (!contained) {
                newNames.add (name);
            }
        }

        /* Get all newly referenced categories from the database. */
        if (newNames.size () > 0) {
            String query = "from Category as category where category.name in :names";
            List<Category> newCategories = plugin.getCollap ().getSessionFactory ().getCurrentSession ()
                    .createQuery (query)
                    .setParameterList ("names", newNames)
                    .list ();

            categories.addAll (newCategories);

            for (Category category : newCategories) { /* After this operation all invalid names will be left. */
                newNames.remove (category.getName ());
            }

            /* Display which categories could not be found. */
            if (newNames.size () > 0) {
                Writer writer = response.getWriter ();
                writer.write ("The following categories could not be found and were not added to the post: <br />");
                for (String categoryName : newNames) {
                    writer.write (categoryName + "<br />");
                }
                writer.write ("<br />");
            }
        }
    }

}
